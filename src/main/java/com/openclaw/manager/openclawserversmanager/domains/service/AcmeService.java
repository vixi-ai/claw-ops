package com.openclaw.manager.openclawserversmanager.domains.service;

import com.openclaw.manager.openclawserversmanager.common.validation.HostnameValidator;
import com.openclaw.manager.openclawserversmanager.domains.entity.DnsRecordType;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainAssignment;
import com.openclaw.manager.openclawserversmanager.domains.entity.ManagedZone;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProviderAccount;
import com.openclaw.manager.openclawserversmanager.domains.mapper.ProviderAccountMapper;
import com.openclaw.manager.openclawserversmanager.domains.provider.DnsProviderAdapter;
import com.openclaw.manager.openclawserversmanager.domains.provider.DnsRecord;
import com.openclaw.manager.openclawserversmanager.domains.provider.ProviderAdapterFactory;
import com.openclaw.manager.openclawserversmanager.secrets.service.SecretService;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.ssh.model.CommandResult;
import com.openclaw.manager.openclawserversmanager.ssh.service.SshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class AcmeService {

    private static final Logger log = LoggerFactory.getLogger(AcmeService.class);
    private static final int CERTBOT_TIMEOUT = 600;

    private static final String AUTH_HOOK_TEMPLATE = """
            #!/bin/bash
            SIGNAL_DIR="/tmp/openclaw-acme"
            mkdir -p "$SIGNAL_DIR"
            echo "$CERTBOT_VALIDATION" > "$SIGNAL_DIR/%s.validation"
            for i in $(seq 1 360); do
              if [ -f "$SIGNAL_DIR/%s.ready" ]; then
                rm -f "$SIGNAL_DIR/%s.ready"
                exit 0
              fi
              sleep 1
            done
            echo "Timeout waiting for DNS record creation signal" >&2
            exit 1
            """;

    private static final String CLEANUP_HOOK_TEMPLATE = """
            #!/bin/bash
            rm -f /tmp/openclaw-acme/%s.validation /tmp/openclaw-acme/%s.ready
            """;

    private final ManagedZoneService managedZoneService;
    private final ProviderAccountService providerAccountService;
    private final ProviderAdapterFactory providerAdapterFactory;
    private final SecretService secretService;
    private final SshService sshService;

    public AcmeService(ManagedZoneService managedZoneService,
                       ProviderAccountService providerAccountService,
                       ProviderAdapterFactory providerAdapterFactory,
                       SecretService secretService,
                       SshService sshService) {
        this.managedZoneService = managedZoneService;
        this.providerAccountService = providerAccountService;
        this.providerAdapterFactory = providerAdapterFactory;
        this.secretService = secretService;
        this.sshService = sshService;
    }

    public String createAcmeChallengeTxtRecord(DomainAssignment assignment, String challengeValue) {
        ManagedZone zone = managedZoneService.findZoneOrThrow(assignment.getZoneId());
        ProviderAccount account = providerAccountService.findAccountOrThrow(zone.getProviderAccountId());
        String decryptedToken = secretService.decryptSecret(account.getCredentialId());
        Map<String, Object> settings = ProviderAccountMapper.deserializeSettings(account.getProviderSettings());
        DnsProviderAdapter adapter = providerAdapterFactory.getAdapter(account.getProviderType());

        String acmeHostname = "_acme-challenge." + assignment.getHostname();
        DnsRecord txtRecord = new DnsRecord(null, acmeHostname, DnsRecordType.TXT, challengeValue, 60, false);
        DnsRecord created = adapter.createOrUpsertRecord(
                zone.getZoneName(), zone.getProviderZoneId(), txtRecord, decryptedToken, settings);

        log.info("Created ACME TXT record for {} (recordId: {})", acmeHostname, created.providerRecordId());
        return created.providerRecordId();
    }

    public void deleteAcmeChallengeTxtRecord(DomainAssignment assignment, String txtRecordId) {
        try {
            ManagedZone zone = managedZoneService.findZoneOrThrow(assignment.getZoneId());
            ProviderAccount account = providerAccountService.findAccountOrThrow(zone.getProviderAccountId());
            String decryptedToken = secretService.decryptSecret(account.getCredentialId());
            Map<String, Object> settings = ProviderAccountMapper.deserializeSettings(account.getProviderSettings());
            DnsProviderAdapter adapter = providerAdapterFactory.getAdapter(account.getProviderType());
            adapter.deleteRecord(zone.getProviderZoneId(), txtRecordId, decryptedToken, settings);
            log.info("Deleted ACME TXT record {}", txtRecordId);
        } catch (Exception e) {
            log.warn("Failed to delete ACME TXT record {}: {}", txtRecordId, e.getMessage());
        }
    }

    public boolean waitForTxtPropagation(Server server, String hostname, String expectedValue,
                                          int maxAttempts, int intervalSeconds) {
        String acmeHostname = "_acme-challenge." + hostname;
        // Run a single SSH command that polls in a loop on the remote side — avoids 18+ separate SSH connections
        String pollScript = "for i in $(seq 1 %d); do " +
                "V=$(dig @8.8.8.8 +short TXT %s 2>/dev/null | tr -d '\"'); " +
                "if echo \"$V\" | grep -qF '%s'; then echo 'PROPAGATED'; exit 0; fi; " +
                "echo \"attempt $i/%d: not yet\"; sleep %d; done; echo 'TIMEOUT'; exit 1";
        String cmd = pollScript.formatted(maxAttempts, acmeHostname, expectedValue, maxAttempts, intervalSeconds);

        CommandResult result = sshService.executeCommand(server, cmd, maxAttempts * intervalSeconds + 30);
        boolean propagated = result.stdout().contains("PROPAGATED");
        log.info("DNS propagation for {}: {}", acmeHostname, propagated ? "OK" : "TIMEOUT");
        return propagated;
    }

    /**
     * Full DNS-01 certbot flow. Returns immediately if cert already exists on server.
     */
    public CertbotResult runCertbotWithDns01(Server server, DomainAssignment assignment,
                                              String hostname, String adminEmail) {
        HostnameValidator.requireValid(hostname);
        String signalDir = "/tmp/openclaw-acme";
        String safeHostname = hostname.replace(".", "-");

        try {
            // 1. Single SSH: check cert + install certbot + setup signals (batched)
            CommandResult setup = sshService.executeCommand(server,
                    "sudo test -f /etc/letsencrypt/live/%s/fullchain.pem && echo 'CERT_EXISTS' || echo 'CERT_MISSING'; ".formatted(hostname) +
                    "command -v certbot >/dev/null 2>&1 || (sudo apt-get update -qq && sudo apt-get install -y -qq certbot); " +
                    "mkdir -p %s; rm -f %s/%s.validation %s/%s.ready".formatted(
                            signalDir, signalDir, safeHostname, signalDir, safeHostname),
                    300);

            if (setup.stdout().contains("CERT_EXISTS")) {
                log.info("Valid cert already exists for {} — skipping certbot", hostname);
                return new CertbotResult(0, "Existing certificate found", null, null);
            }

            // 2. Upload hook scripts (2 SSH connections — unavoidable for SFTP)
            String authPath = signalDir + "/auth-" + safeHostname + ".sh";
            String cleanupPath = signalDir + "/cleanup-" + safeHostname + ".sh";
            sshService.uploadFile(server,
                    AUTH_HOOK_TEMPLATE.formatted(safeHostname, safeHostname, safeHostname).getBytes(StandardCharsets.UTF_8),
                    authPath);
            sshService.uploadFile(server,
                    CLEANUP_HOOK_TEMPLATE.formatted(safeHostname, safeHostname).getBytes(StandardCharsets.UTF_8),
                    cleanupPath);

            // 3. Single SSH: chmod + launch certbot in background
            String certbotCmd = "sudo certbot certonly --manual --preferred-challenges dns-01 " +
                    "-d %s --cert-name %s --manual-auth-hook %s --manual-cleanup-hook %s " +
                    "--non-interactive --agree-tos --email %s";
            String fullCmd = certbotCmd.formatted(hostname, hostname, authPath, cleanupPath, adminEmail);
            String logFile = signalDir + "/certbot-" + safeHostname + ".log";
            String pidFile = signalDir + "/certbot-" + safeHostname + ".pid";

            sshService.executeCommand(server,
                    "chmod +x %s %s; nohup bash -c '%s' > %s 2>&1 & echo $! > %s".formatted(
                            authPath, cleanupPath, fullCmd.replace("'", "'\\''"), logFile, pidFile), 10);
            log.info("Certbot launched for {} on '{}'", hostname, server.getName());

            // 4. Poll for validation token using a remote loop (1 SSH instead of 30+)
            String validationFile = signalDir + "/" + safeHostname + ".validation";
            CommandResult pollResult = sshService.executeCommand(server,
                    "for i in $(seq 1 30); do " +
                    "  V=$(cat %s 2>/dev/null); ".formatted(validationFile) +
                    "  if [ -n \"$V\" ]; then echo \"TOKEN:$V\"; exit 0; fi; " +
                    "  if ! kill -0 $(cat %s 2>/dev/null) 2>/dev/null; then echo 'CERTBOT_EXITED'; exit 1; fi; ".formatted(pidFile) +
                    "  sleep 2; done; echo 'POLL_TIMEOUT'; exit 1", 75);

            String pollOut = pollResult.stdout().trim();

            if (pollOut.startsWith("TOKEN:")) {
                // Got the validation token — create TXT record
                String challengeValue = pollOut.substring(6).trim();
                log.info("Got CERTBOT_VALIDATION for {}: {}", hostname, challengeValue);

                String txtRecordId = createAcmeChallengeTxtRecord(assignment, challengeValue);

                // Wait for DNS propagation (1 SSH — remote loop)
                boolean propagated = waitForTxtPropagation(server, hostname, challengeValue, 18, 10);
                if (!propagated) {
                    return new CertbotResult(1, "", "DNS propagation timeout", txtRecordId);
                }

                // Signal auth hook to continue
                sshService.executeCommand(server, "echo 'ready' > %s/%s.ready".formatted(signalDir, safeHostname), 10);
                log.info("Signaled certbot to continue for {}", hostname);

                // Wait for certbot to finish (1 SSH — remote loop)
                CommandResult waitResult = sshService.executeCommand(server,
                        "for i in $(seq 1 60); do " +
                        "  kill -0 $(cat %s 2>/dev/null) 2>/dev/null || { echo 'DONE'; exit 0; }; " .formatted(pidFile) +
                        "  sleep 2; done; echo 'TIMEOUT'", 135);

                // Check result (1 SSH — batched)
                CommandResult resultCheck = sshService.executeCommand(server,
                        "sudo test -f /etc/letsencrypt/live/%s/fullchain.pem && echo 'CERT_OK' || echo 'CERT_MISSING'; ".formatted(hostname) +
                        "cat %s 2>/dev/null".formatted(logFile), 10);

                boolean success = resultCheck.stdout().contains("CERT_OK");
                log.info("Certbot {} for {} on '{}'", success ? "succeeded" : "failed", hostname, server.getName());
                return new CertbotResult(success ? 0 : 1, resultCheck.stdout(),
                        success ? null : "Certbot failed", txtRecordId);

            } else if (pollOut.contains("CERTBOT_EXITED")) {
                // Certbot exited without calling auth hook — check if cert exists (reuse/error)
                CommandResult check = sshService.executeCommand(server,
                        "sudo test -f /etc/letsencrypt/live/%s/fullchain.pem && echo 'CERT_OK' || echo 'CERT_MISSING'; cat %s 2>/dev/null"
                                .formatted(hostname, logFile), 10);
                if (check.stdout().contains("CERT_OK")) {
                    log.info("Certbot reused existing authorization for {}", hostname);
                    return new CertbotResult(0, check.stdout(), null, null);
                }
                return new CertbotResult(1, check.stdout(), "Certbot failed — no cert produced", null);

            } else {
                // Timeout waiting for validation token
                CommandResult check = sshService.executeCommand(server,
                        "sudo test -f /etc/letsencrypt/live/%s/fullchain.pem && echo 'CERT_OK' || echo 'CERT_MISSING'; cat %s 2>/dev/null"
                                .formatted(hostname, logFile), 10);
                if (check.stdout().contains("CERT_OK")) {
                    return new CertbotResult(0, check.stdout(), null, null);
                }
                return new CertbotResult(1, check.stdout(), "Certbot failed — no validation token produced and no cert found", null);
            }

        } catch (Exception e) {
            log.error("Certbot flow error for {}: {}", hostname, e.getMessage(), e);
            return new CertbotResult(1, "", e.getMessage(), null);
        } finally {
            try {
                sshService.executeCommand(server,
                        "rm -f %s/auth-%s.sh %s/cleanup-%s.sh %s/%s.validation %s/%s.ready %s/certbot-%s.log %s/certbot-%s.pid"
                                .formatted(signalDir, safeHostname, signalDir, safeHostname, signalDir, safeHostname,
                                        signalDir, safeHostname, signalDir, safeHostname, signalDir, safeHostname), 10);
            } catch (Exception ignored) { }
        }
    }

    public void deleteCertbotCert(Server server, String hostname) {
        sshService.executeCommand(server,
                "sudo certbot delete --cert-name %s --non-interactive 2>/dev/null || true".formatted(hostname), 30);
    }

    public void ensureNginxInstalled(Server server) {
        // Single SSH: check + install + start (batched)
        CommandResult result = sshService.executeCommand(server,
                "command -v nginx >/dev/null 2>&1 || (sudo apt-get update -qq && sudo apt-get install -y -qq nginx); " +
                "sudo systemctl enable nginx 2>/dev/null; " +
                "sudo systemctl is-active --quiet nginx || sudo systemctl start nginx; " +
                "echo 'NGINX_OK'", 300);
        if (!result.stdout().contains("NGINX_OK")) {
            throw new RuntimeException("Failed to ensure nginx: " + result.stderr());
        }
    }

    public record CertbotResult(int exitCode, String output, String error, String txtRecordId) {
        public boolean success() { return exitCode == 0; }
    }
}
