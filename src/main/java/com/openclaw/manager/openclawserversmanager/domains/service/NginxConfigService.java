package com.openclaw.manager.openclawserversmanager.domains.service;

import com.openclaw.manager.openclawserversmanager.common.validation.HostnameValidator;
import com.openclaw.manager.openclawserversmanager.domains.exception.DomainException;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.ssh.model.CommandResult;
import com.openclaw.manager.openclawserversmanager.ssh.service.SshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class NginxConfigService {

    private static final Logger log = LoggerFactory.getLogger(NginxConfigService.class);
    private static final String MANAGED_DIR = "/etc/nginx/openclaw-managed";

    private static final String NGINX_HTTPS_CONFIG_TEMPLATE = """
            # Managed-by: OpenClaw -- DO NOT EDIT MANUALLY
            # Hostname: %s
            # DomainAssignmentId: %s
            # Generated: %s

            server {
                listen 80;
                server_name %s;
                return 301 https://$host$request_uri;
            }
            server {
                listen 443 ssl http2;
                server_name %s;

                ssl_certificate /etc/letsencrypt/live/%s/fullchain.pem;
                ssl_certificate_key /etc/letsencrypt/live/%s/privkey.pem;

                # SSL hardening
                ssl_protocols TLSv1.2 TLSv1.3;
                ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305:ECDHE-RSA-CHACHA20-POLY1305;
                ssl_prefer_server_ciphers off;
                ssl_session_cache shared:SSL:10m;
                ssl_session_timeout 1d;
                ssl_session_tickets off;

                # OCSP stapling
                ssl_stapling on;
                ssl_stapling_verify on;
                ssl_trusted_certificate /etc/letsencrypt/live/%s/chain.pem;
                resolver 1.1.1.1 8.8.8.8 valid=300s;
                resolver_timeout 5s;

                # Security headers
                add_header Strict-Transport-Security "max-age=63072000; includeSubDomains" always;
                add_header X-Content-Type-Options nosniff always;
                add_header X-Frame-Options SAMEORIGIN always;

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

    private final SshService sshService;

    public NginxConfigService(SshService sshService) {
        this.sshService = sshService;
    }

    /**
     * One-time setup: creates /etc/nginx/openclaw-managed/ and adds include to nginx.conf.
     * Idempotent — safe to call multiple times.
     */
    public void ensureManagedDirectory(Server server) {
        // Single SSH: check if already set up, if not do the full setup
        CommandResult check = sshService.executeCommand(server,
                "grep -q 'openclaw-managed' /etc/nginx/nginx.conf && echo 'ALREADY_SETUP' || echo 'NEEDS_SETUP'", 10);

        if (check.stdout().trim().contains("ALREADY_SETUP")) {
            // Verify directory exists (quick single command)
            sshService.executeCommand(server, "sudo mkdir -p " + MANAGED_DIR, 10);
            return;
        }

        // Single SSH: create dir + remove stale includes + add include + test
        CommandResult setup = sshService.executeCommand(server,
                "sudo mkdir -p %s; ".formatted(MANAGED_DIR) +
                "sudo sed -i '/openclaw-managed/d' /etc/nginx/nginx.conf; " +
                "sudo sed -i '/include.*sites-enabled/a\\    include %s/*.conf;' /etc/nginx/nginx.conf; ".formatted(MANAGED_DIR) +
                "grep -q 'openclaw-managed' /etc/nginx/nginx.conf || " +
                "sudo sed -i '/include.*conf\\.d/a\\    include %s/*.conf;' /etc/nginx/nginx.conf; ".formatted(MANAGED_DIR) +
                "sudo nginx -t 2>&1", 15);

        if (setup.exitCode() != 0) {
            // Revert and throw
            sshService.executeCommand(server, "sudo sed -i '/openclaw-managed/d' /etc/nginx/nginx.conf", 10);
            throw new DomainException("Failed to add openclaw-managed include: " + setup.stderr());
        }

        log.info("Set up openclaw-managed directory on server '{}'", server.getName());
    }

    /**
     * Generates and deploys /etc/nginx/openclaw-managed/{hostname}.conf with HTTPS reverse proxy + WSS.
     */
    public void deployConfig(Server server, String hostname, String assignmentId, int targetPort) {
        HostnameValidator.requireValid(hostname);
        String config = NGINX_HTTPS_CONFIG_TEMPLATE.formatted(
                hostname, assignmentId, Instant.now().toString(),
                hostname, hostname, hostname, hostname, hostname, targetPort
        );

        String remotePath = MANAGED_DIR + "/" + hostname + ".conf";
        String tmpPath = "/tmp/openclaw-nginx-" + hostname + ".conf";

        sshService.uploadFile(server, config.getBytes(StandardCharsets.UTF_8), tmpPath);
        sshService.executeCommand(server, "sudo mv %s %s".formatted(tmpPath, remotePath), 15);

        log.info("Deployed nginx config for {} on server '{}'", hostname, server.getName());
    }

    /**
     * Removes /etc/nginx/openclaw-managed/{hostname}.conf. Best-effort.
     */
    public void removeConfig(Server server, String hostname) {
        String remotePath = MANAGED_DIR + "/" + hostname + ".conf";
        sshService.executeCommand(server, "sudo rm -f " + remotePath, 15);
        log.info("Removed nginx config for {} on server '{}'", hostname, server.getName());
    }

    /**
     * Tests nginx config and gracefully reloads. Returns the test result.
     * Throws DomainException if nginx -t fails.
     */
    public CommandResult testAndReload(Server server) {
        CommandResult testResult = sshService.executeCommand(server, "sudo nginx -t", 15);
        if (testResult.exitCode() != 0) {
            throw new DomainException("Nginx config test failed: " + testResult.stderr());
        }

        // Check if nginx is running
        CommandResult statusResult = sshService.executeCommand(server,
                "sudo systemctl is-active nginx 2>/dev/null || echo 'INACTIVE'", 10);
        boolean nginxRunning = !statusResult.stdout().trim().contains("INACTIVE");

        if (nginxRunning) {
            // Nginx is running — graceful reload
            CommandResult reloadResult = sshService.executeCommand(server, "sudo systemctl reload nginx", 15);
            if (reloadResult.exitCode() != 0) {
                throw new DomainException("Nginx reload failed: " + reloadResult.stderr());
            }
            return testResult;
        }

        // Nginx is NOT running — need to start it
        log.info("Nginx not running on '{}', resolving port conflicts before starting", server.getName());

        // Kill any stale nginx workers that systemd doesn't know about
        sshService.executeCommand(server, "sudo pkill -9 nginx 2>/dev/null || true", 10);

        // Check what's on port 80
        CommandResult port80 = sshService.executeCommand(server,
                "sudo ss -tlnp 'sport = :80' 2>/dev/null | grep LISTEN || echo 'PORT_FREE'", 10);
        String port80Info = port80.stdout().trim();

        if (!port80Info.contains("PORT_FREE")) {
            log.warn("Port 80 in use on '{}': {}", server.getName(), port80Info);
            // Try stopping apache2 if it's the culprit
            if (port80Info.contains("apache") || port80Info.contains("httpd")) {
                sshService.executeCommand(server, "sudo systemctl stop apache2 2>/dev/null || true", 15);
                log.info("Stopped apache2 on '{}' to free port 80", server.getName());
            } else {
                // Kill whatever is on port 80 — nginx needs it
                sshService.executeCommand(server, "sudo fuser -k 80/tcp 2>/dev/null || true", 10);
                // Wait a moment for the port to be released
                try { Thread.sleep(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }

        // Now start nginx
        CommandResult startResult = sshService.executeCommand(server, "sudo systemctl start nginx", 15);
        if (startResult.exitCode() != 0) {
            CommandResult diag = sshService.executeCommand(server,
                    "sudo journalctl -u nginx --no-pager -n 15 2>/dev/null; echo '---PORT80---'; sudo ss -tlnp 'sport = :80' 2>/dev/null", 15);
            log.error("Nginx start failed on '{}'. Diagnostics:\n{}", server.getName(), diag.stdout());
            throw new DomainException("Nginx failed to start (port 80 conflict): " + diag.stdout());
        }

        return testResult;
    }
}
