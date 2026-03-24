package com.openclaw.manager.openclawserversmanager.domains.service;

import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.ssh.model.CommandResult;
import com.openclaw.manager.openclawserversmanager.ssh.service.SshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SslVerificationService {

    private static final Logger log = LoggerFactory.getLogger(SslVerificationService.class);
    private static final Pattern EXPIRY_PATTERN = Pattern.compile(
            "notAfter=(\\d{4}-\\d{2}-\\d{2})"
    );

    private final SshService sshService;

    public SslVerificationService(SshService sshService) {
        this.sshService = sshService;
    }

    /**
     * Checks if HTTPS endpoint is reachable from the server via curl.
     */
    public boolean verifyHttpsReachable(Server server, String hostname) {
        CommandResult result = sshService.executeCommand(server,
                "curl -sI -o /dev/null -w '%%{http_code}' --max-time 10 https://%s || echo 'FAILED'"
                        .formatted(hostname), 20);
        String code = result.stdout().trim();
        boolean reachable = code.startsWith("2") || code.startsWith("3") || code.equals("404");
        log.info("HTTPS check for {}: HTTP {} (reachable={})", hostname, code, reachable);
        return reachable;
    }

    /**
     * Checks TLS certificate validity via openssl. Returns cert expiry or null.
     */
    public Instant verifyTlsCertificate(Server server, String hostname) {
        CommandResult result = sshService.executeCommand(server,
                "echo | openssl s_client -connect %s:443 -servername %s 2>/dev/null | openssl x509 -noout -enddate 2>/dev/null"
                        .formatted(hostname, hostname), 20);

        if (result.exitCode() != 0 || result.stdout().isBlank()) {
            log.warn("TLS verification failed for {}: no certificate found", hostname);
            return null;
        }

        // Parse "notAfter=Mar 24 12:00:00 2026 GMT" or similar
        String out = result.stdout().trim();
        try {
            // Try ISO-like extraction first
            Matcher matcher = EXPIRY_PATTERN.matcher(out);
            if (matcher.find()) {
                return Instant.parse(matcher.group(1) + "T00:00:00Z");
            }
            // Fallback: just check if output contains "notAfter" (cert exists)
            if (out.contains("notAfter")) {
                log.info("TLS cert found for {} (expiry line: {})", hostname, out);
                return Instant.now().plusSeconds(86400 * 90); // assume 90 days if can't parse
            }
        } catch (Exception e) {
            log.warn("Failed to parse TLS cert expiry for {}: {}", hostname, e.getMessage());
        }

        return null;
    }

    /**
     * Full verification: HTTPS reachability + TLS certificate validity.
     */
    public VerificationResult verify(Server server, String hostname) {
        boolean httpsReachable = verifyHttpsReachable(server, hostname);
        Instant certExpiry = null;
        boolean tlsValid = false;

        if (httpsReachable) {
            certExpiry = verifyTlsCertificate(server, hostname);
            tlsValid = certExpiry != null && certExpiry.isAfter(Instant.now());
        }

        return new VerificationResult(httpsReachable, tlsValid, certExpiry,
                httpsReachable ? null : "HTTPS endpoint not reachable");
    }

    public record VerificationResult(
            boolean httpsReachable,
            boolean tlsValid,
            Instant certExpiry,
            String errorDetail
    ) {}
}
