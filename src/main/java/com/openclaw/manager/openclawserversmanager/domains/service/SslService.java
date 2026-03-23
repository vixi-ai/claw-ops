package com.openclaw.manager.openclawserversmanager.domains.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.domains.config.SslConfig;
import com.openclaw.manager.openclawserversmanager.domains.dto.BulkSslProvisionResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.SslCertificateResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.SslCertificate;
import com.openclaw.manager.openclawserversmanager.domains.entity.SslStatus;
import com.openclaw.manager.openclawserversmanager.domains.exception.DomainException;
import com.openclaw.manager.openclawserversmanager.domains.mapper.SslCertificateMapper;
import com.openclaw.manager.openclawserversmanager.domains.repository.SslCertificateRepository;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.repository.ServerRepository;
import com.openclaw.manager.openclawserversmanager.servers.service.ServerService;
import com.openclaw.manager.openclawserversmanager.ssh.model.CommandResult;
import com.openclaw.manager.openclawserversmanager.ssh.service.SshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SslService {

    private static final Logger log = LoggerFactory.getLogger(SslService.class);
    private static final int SSH_TIMEOUT = 300; // 5 minutes for certbot operations
    private static final Pattern EXPIRY_PATTERN = Pattern.compile(
            "Expiry Date:\\s*(\\d{4}-\\d{2}-\\d{2})"
    );

    private static final String FREE_PORT_80_SCRIPT = """
            #!/bin/bash
            # Stop nginx if running (certbot standalone needs port 80 free)
            if systemctl is-active --quiet nginx 2>/dev/null; then
              echo "STOPPING: nginx"
              systemctl stop nginx
            fi
            # Stop Apache if running
            if systemctl is-active --quiet apache2 2>/dev/null; then
              echo "CONFLICT: Stopping apache2"
              systemctl stop apache2 && systemctl disable apache2
            fi
            # Kill anything else on port 80
            if ss -tlnp 'sport = :80' 2>/dev/null | grep -q LISTEN; then
              echo "CONFLICT: Killing processes on port 80"
              fuser -k 80/tcp 2>/dev/null || true
              sleep 2
            fi
            if ss -tlnp 'sport = :80' 2>/dev/null | grep -q LISTEN; then
              echo "PORT_BUSY"
              exit 1
            fi
            echo "PORT_FREE"
            exit 0
            """;

    // Deployed after certbot succeeds — HTTP redirect + HTTPS SSL proxy
    private static final String NGINX_HTTPS_CONFIG_TEMPLATE = """
            server {
                listen 80;
                server_name %s;
                return 301 https://$host$request_uri;
            }
            server {
                listen 443 ssl;
                server_name %s;
                ssl_certificate /etc/letsencrypt/live/%s/fullchain.pem;
                ssl_certificate_key /etc/letsencrypt/live/%s/privkey.pem;
                location / {
                    proxy_pass http://127.0.0.1:%d;
                    proxy_set_header Host $host;
                    proxy_set_header X-Real-IP $remote_addr;
                    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                    proxy_set_header X-Forwarded-Proto $scheme;
                    proxy_http_version 1.1;
                    proxy_set_header Upgrade $http_upgrade;
                    proxy_set_header Connection "upgrade";
                }
            }
            """;

    private final SslCertificateRepository sslCertificateRepository;
    private final ServerService serverService;
    private final ServerRepository serverRepository;
    private final SshService sshService;
    private final AuditService auditService;
    private final SslConfig sslConfig;

    public SslService(SslCertificateRepository sslCertificateRepository,
                      ServerService serverService,
                      ServerRepository serverRepository,
                      SshService sshService,
                      AuditService auditService,
                      SslConfig sslConfig) {
        this.sslCertificateRepository = sslCertificateRepository;
        this.serverService = serverService;
        this.serverRepository = serverRepository;
        this.sshService = sshService;
        this.auditService = auditService;
        this.sslConfig = sslConfig;
    }

    @Transactional
    public SslCertificateResponse provision(UUID serverId, UUID assignmentId, String hostname,
                                            Integer targetPort, UUID userId) {
        Server server = serverService.getServerEntity(serverId);

        // Check if SSL already exists for this server
        Optional<SslCertificate> existing = sslCertificateRepository.findByServerId(serverId);
        if (existing.isPresent() && existing.get().getStatus() == SslStatus.ACTIVE) {
            throw new DomainException("SSL certificate already active for server '%s'".formatted(server.getName()));
        }

        int port = targetPort != null ? targetPort : sslConfig.getTargetPort();
        String email = sslConfig.getAdminEmail();

        // Create or reuse record
        SslCertificate cert = existing.orElseGet(SslCertificate::new);
        cert.setServerId(serverId);
        cert.setAssignmentId(assignmentId);
        cert.setHostname(hostname);
        cert.setStatus(SslStatus.PROVISIONING);
        cert.setAdminEmail(email);
        cert.setTargetPort(port);
        cert.setLastError(null);
        cert = sslCertificateRepository.save(cert);

        try {
            // 1. Install nginx + certbot (only if not already present)
            CommandResult checkResult = sshService.executeCommand(server,
                    "command -v nginx && command -v certbot", 30);
            if (checkResult.exitCode() != 0) {
                log.info("Installing nginx + certbot on server '{}'", server.getName());
                CommandResult installResult = sshService.executeCommand(server,
                        "sudo apt-get update -qq && sudo apt-get install -y -qq nginx certbot python3-certbot-nginx",
                        SSH_TIMEOUT);
                if (installResult.exitCode() != 0) {
                    throw new DomainException("Failed to install nginx/certbot: " + installResult.stderr());
                }
            } else {
                log.info("nginx + certbot already installed on server '{}'", server.getName());
            }

            // 2. Stop nginx + free port 80 so certbot standalone can bind it
            String freeScriptPath = "/tmp/free-port-80-" + System.currentTimeMillis() + ".sh";
            sshService.uploadFile(server, FREE_PORT_80_SCRIPT.getBytes(StandardCharsets.UTF_8), freeScriptPath);
            CommandResult freeResult = sshService.executeCommand(server, "sudo bash " + freeScriptPath, 30);
            sshService.executeCommand(server, "rm -f " + freeScriptPath, 10);

            if (freeResult.exitCode() != 0 || freeResult.stdout().contains("PORT_BUSY")) {
                throw new DomainException(
                        "Port 80 is occupied by an unmanaged process. Free port 80 on the server before provisioning SSL.");
            }
            log.info("Port 80 freed on server '{}': {}", server.getName(), freeResult.stdout().trim());

            // 3. DNS check — warning only, certbot will do its own validation from LE servers
            if (!waitForDns(server, hostname)) {
                log.warn("DNS pre-check timed out for {} — proceeding with certbot anyway", hostname);
            }

            // 4. Delete any previous certbot cert for this hostname to avoid stale state
            sshService.executeCommand(server,
                    "sudo certbot delete --cert-name %s --non-interactive 2>/dev/null || true".formatted(hostname), 30);

            // 5. Run certbot standalone (binds port 80 directly, no nginx involvement)
            CommandResult certbotResult = sshService.executeCommand(server,
                    "sudo certbot certonly --standalone -d %s --non-interactive --agree-tos --email %s"
                            .formatted(hostname, email),
                    SSH_TIMEOUT);
            if (certbotResult.exitCode() != 0) {
                String certbotError = certbotResult.stdout().isBlank() ? certbotResult.stderr() : certbotResult.stdout();
                throw new DomainException("Certbot failed: " + certbotError.strip());
            }

            // 6. Deploy HTTPS nginx config (HTTP→HTTPS redirect + SSL proxy)
            String configPath = "/etc/nginx/sites-available/" + hostname;
            String httpsConfig = NGINX_HTTPS_CONFIG_TEMPLATE.formatted(hostname, hostname, hostname, hostname, port);
            String tmpConfigPath = "/tmp/nginx-ssl-" + hostname;
            sshService.uploadFile(server, httpsConfig.getBytes(StandardCharsets.UTF_8), tmpConfigPath);
            sshService.executeCommand(server, "sudo mv %s %s".formatted(tmpConfigPath, configPath), 15);
            sshService.executeCommand(server, "sudo rm -f /etc/nginx/sites-enabled/default", 15);
            sshService.executeCommand(server,
                    "sudo ln -sf %s /etc/nginx/sites-enabled/%s".formatted(configPath, hostname), 15);

            CommandResult testResult = sshService.executeCommand(server, "sudo nginx -t", 15);
            if (testResult.exitCode() != 0) {
                throw new DomainException("Nginx HTTPS config test failed: " + testResult.stderr());
            }

            sshService.executeCommand(server, "sudo systemctl enable nginx", 15);
            CommandResult startResult = sshService.executeCommand(server, "sudo systemctl start nginx", 30);
            if (startResult.exitCode() != 0) {
                CommandResult diag = sshService.executeCommand(server,
                        "sudo journalctl -u nginx --no-pager -n 20", 15);
                throw new DomainException("Nginx failed to start after SSL setup: " + diag.stdout());
            }

            // Success
            cert.setStatus(SslStatus.ACTIVE);
            cert.setExpiresAt(Instant.now().plus(90, ChronoUnit.DAYS));
            cert.setLastRenewedAt(Instant.now());
            cert.setLastError(null);
            sslCertificateRepository.save(cert);

            // Update server sslEnabled flag
            server.setSslEnabled(true);
            serverService.updateSslEnabled(serverId, true);

            try {
                auditService.log(AuditAction.SSL_PROVISIONED, "SSL_CERTIFICATE", cert.getId(), userId,
                        "SSL provisioned for %s on server '%s'".formatted(hostname, server.getName()));
            } catch (Exception ignored) { }

        } catch (Exception e) {
            cert.setStatus(SslStatus.FAILED);
            cert.setLastError(e.getMessage());
            sslCertificateRepository.save(cert);

            log.error("SSL provisioning failed for {} on server '{}': {}",
                    hostname, server.getName(), e.getMessage());

            if (e instanceof DomainException) throw e;
            throw new DomainException("SSL provisioning failed: " + e.getMessage());
        }

        return SslCertificateMapper.toResponse(cert);
    }

    @Transactional
    public SslCertificateResponse renew(UUID certId, UUID userId) {
        SslCertificate cert = findCertOrThrow(certId);
        Server server = serverService.getServerEntity(cert.getServerId());

        try {
            CommandResult result = sshService.executeCommand(server,
                    "sudo certbot renew --cert-name %s --non-interactive".formatted(cert.getHostname()),
                    SSH_TIMEOUT);

            if (result.exitCode() != 0) {
                cert.setLastError("Renew failed: " + result.stderr());
                sslCertificateRepository.save(cert);
                throw new DomainException("Certbot renew failed: " + result.stderr());
            }

            cert.setExpiresAt(Instant.now().plus(90, ChronoUnit.DAYS));
            cert.setLastRenewedAt(Instant.now());
            cert.setStatus(SslStatus.ACTIVE);
            cert.setLastError(null);
            sslCertificateRepository.save(cert);

            try {
                auditService.log(AuditAction.SSL_RENEWED, "SSL_CERTIFICATE", certId, userId,
                        "SSL renewed for %s".formatted(cert.getHostname()));
            } catch (Exception ignored) { }

        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            cert.setLastError(e.getMessage());
            sslCertificateRepository.save(cert);
            throw new DomainException("SSL renewal failed: " + e.getMessage());
        }

        return SslCertificateMapper.toResponse(cert);
    }

    @Transactional
    public void remove(UUID certId, UUID userId) {
        SslCertificate cert = findCertOrThrow(certId);
        Server server = serverService.getServerEntity(cert.getServerId());

        cert.setStatus(SslStatus.REMOVING);
        sslCertificateRepository.save(cert);

        try {
            // Delete certbot cert
            sshService.executeCommand(server,
                    "sudo certbot delete --cert-name %s --non-interactive".formatted(cert.getHostname()),
                    60);

            // Remove nginx config and reload
            sshService.executeCommand(server,
                    "sudo rm -f /etc/nginx/sites-enabled/%s /etc/nginx/sites-available/%s && sudo nginx -t && sudo systemctl reload nginx"
                            .formatted(cert.getHostname(), cert.getHostname()),
                    60);
        } catch (Exception e) {
            log.warn("SSL cleanup commands failed for {}: {}", cert.getHostname(), e.getMessage());
        }

        // Update server flag
        try {
            serverService.updateSslEnabled(cert.getServerId(), false);
        } catch (Exception ignored) { }

        sslCertificateRepository.delete(cert);

        try {
            auditService.log(AuditAction.SSL_REMOVED, "SSL_CERTIFICATE", certId, userId,
                    "SSL removed for %s".formatted(cert.getHostname()));
        } catch (Exception ignored) { }
    }

    @Transactional
    public SslCertificateResponse check(UUID certId, UUID userId) {
        SslCertificate cert = findCertOrThrow(certId);
        Server server = serverService.getServerEntity(cert.getServerId());

        try {
            CommandResult result = sshService.executeCommand(server,
                    "sudo certbot certificates --cert-name %s".formatted(cert.getHostname()),
                    60);

            if (result.exitCode() == 0) {
                Matcher matcher = EXPIRY_PATTERN.matcher(result.stdout());
                if (matcher.find()) {
                    Instant expiry = Instant.parse(matcher.group(1) + "T00:00:00Z");
                    cert.setExpiresAt(expiry);

                    if (expiry.isBefore(Instant.now())) {
                        cert.setStatus(SslStatus.EXPIRED);
                    } else {
                        cert.setStatus(SslStatus.ACTIVE);
                    }
                }
                cert.setLastError(null);
            } else {
                cert.setStatus(SslStatus.FAILED);
                cert.setLastError("Certificate not found by certbot");
            }

            sslCertificateRepository.save(cert);

            try {
                auditService.log(AuditAction.SSL_CHECK, "SSL_CERTIFICATE", certId, userId,
                        "SSL check for %s: %s".formatted(cert.getHostname(), cert.getStatus()));
            } catch (Exception ignored) { }

        } catch (Exception e) {
            cert.setLastError("Check failed: " + e.getMessage());
            sslCertificateRepository.save(cert);
        }

        return SslCertificateMapper.toResponse(cert);
    }

    public SslCertificateResponse getCertificate(UUID id) {
        return SslCertificateMapper.toResponse(findCertOrThrow(id));
    }

    public Optional<SslCertificateResponse> getCertificateForServer(UUID serverId) {
        return sslCertificateRepository.findByServerId(serverId)
                .map(SslCertificateMapper::toResponse);
    }

    public Page<SslCertificateResponse> getAllCertificates(Pageable pageable) {
        return sslCertificateRepository.findAll(pageable).map(SslCertificateMapper::toResponse);
    }

    public BulkSslProvisionResponse provisionMissingForAll(UUID userId) {
        List<Server> servers = serverRepository.findBySubdomainIsNotNull();
        int total = 0, provisioned = 0, skipped = 0, failed = 0;

        for (Server server : servers) {
            if (server.getRootDomain() == null) continue;
            total++;
            String hostname = server.getSubdomain() + "." + server.getRootDomain();

            Optional<SslCertificate> existing = sslCertificateRepository.findByServerId(server.getId());
            if (existing.isPresent()) {
                SslStatus status = existing.get().getStatus();
                if (status == SslStatus.ACTIVE || status == SslStatus.PROVISIONING) {
                    skipped++;
                    continue;
                }
            }

            try {
                provision(server.getId(), null, hostname, null, userId);
                provisioned++;
            } catch (Exception e) {
                log.warn("Bulk SSL provision failed for {} on server '{}': {}", hostname, server.getName(), e.getMessage());
                failed++;
            }
        }

        return new BulkSslProvisionResponse(total, provisioned, skipped, failed);
    }

    @Transactional
    public void removeByServerId(UUID serverId, UUID userId) {
        sslCertificateRepository.findByServerId(serverId).ifPresent(cert -> {
            try {
                remove(cert.getId(), userId);
            } catch (Exception e) {
                log.warn("Failed to remove SSL for server {}: {}", serverId, e.getMessage());
                sslCertificateRepository.delete(cert);
            }
        });
    }

    /**
     * Polls DNS resolution from the server itself (up to ~60s) to confirm the hostname resolves
     * before running certbot. Returns true if DNS resolves, false if it times out.
     */
    private boolean waitForDns(Server server, String hostname) {
        int maxAttempts = 3; // 18 × 10s = 3 minutes
        // Use Google DNS (8.8.8.8) to avoid stale local resolver cache
        String dnsCmd = "dig @8.8.8.8 +short %s 2>/dev/null || host %s 8.8.8.8 2>/dev/null".formatted(hostname, hostname);
        for (int i = 1; i <= maxAttempts; i++) {
            CommandResult result = sshService.executeCommand(server, dnsCmd, 15);
            // dig @8.8.8.8 returns the IP on stdout if resolved; host returns "has address"
            String out = result.stdout().trim();
            if (result.exitCode() == 0 && !out.isBlank() && (out.matches("(?s).*\\d+\\.\\d+\\.\\d+\\.\\d+.*") || out.contains("has address"))) {
                log.info("DNS resolved for {} (attempt {}/{}): {}", hostname, i, maxAttempts, out.lines().findFirst().orElse(""));
                return true;
            }
            log.info("DNS not yet resolved for {} (attempt {}/{}), waiting 10s...", hostname, i, maxAttempts);
            if (i < maxAttempts) {
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        log.warn("DNS propagation timeout for {}", hostname);
        return false;
    }

    private SslCertificate findCertOrThrow(UUID id) {
        return sslCertificateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SSL certificate with id " + id + " not found"));
    }
}
