package com.openclaw.manager.openclawserversmanager.apps.service;

import com.openclaw.manager.openclawserversmanager.apps.config.ChatInstallerProperties;
import com.openclaw.manager.openclawserversmanager.apps.dto.ChatAppStatus;
import com.openclaw.manager.openclawserversmanager.apps.dto.ChatInstallRequest;
import com.openclaw.manager.openclawserversmanager.apps.dto.ChatInstallResult;
import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.domains.entity.SslCertificate;
import com.openclaw.manager.openclawserversmanager.domains.repository.SslCertificateRepository;
import com.openclaw.manager.openclawserversmanager.domains.service.NginxConfigService;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.service.ServerService;
import com.openclaw.manager.openclawserversmanager.ssh.model.CommandResult;
import com.openclaw.manager.openclawserversmanager.ssh.service.SshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Deploys bundled third-party apps (currently just "claw-chat") onto managed
 * servers by piping the upstream bootstrap script into bash over SSH. The
 * bootstrap installs OS deps (Docker, Node, Claude CLI) and then downloads a
 * versioned installer tarball from the pinned claw-ops-chat release.
 *
 * <p>Container status is probed by inspecting {@code docker ps}.
 */
@Service
public class AppInstallService {

    private static final Logger log = LoggerFactory.getLogger(AppInstallService.class);
    private static final int INSTALL_TIMEOUT_SECONDS = 600;
    private static final int UPDATE_TIMEOUT_SECONDS = 300;
    private static final int UNINSTALL_TIMEOUT_SECONDS = 120;
    private static final int STATUS_PROBE_TIMEOUT_SECONDS = 15;
    private static final int MAX_OUTPUT_BYTES = 16 * 1024;

    private final ServerService serverService;
    private final SshService sshService;
    private final AuditService auditService;
    private final ChatInstallerProperties installerProps;
    private final NginxConfigService nginxConfigService;
    private final SslCertificateRepository sslCertificateRepository;

    public AppInstallService(ServerService serverService,
                             SshService sshService,
                             AuditService auditService,
                             ChatInstallerProperties installerProps,
                             NginxConfigService nginxConfigService,
                             SslCertificateRepository sslCertificateRepository) {
        this.serverService = serverService;
        this.sshService = sshService;
        this.auditService = auditService;
        this.installerProps = installerProps;
        this.nginxConfigService = nginxConfigService;
        this.sslCertificateRepository = sslCertificateRepository;
    }

    /**
     * Run bootstrap.sh over SSH to install the chat stack. Returns exit code +
     * truncated combined stdout/stderr so the frontend can render the log.
     */
    public ChatInstallResult installChatApp(UUID serverId, ChatInstallRequest req, UUID userId) {
        Server server = serverService.getServerEntity(serverId);
        if (server.getHostname() == null && server.getIpAddress() == null) {
            throw new ResourceNotFoundException("Server has no hostname or IP configured");
        }
        String hostname = resolveInstallHostname(server);
        // apiOrigin is the ClawOps backend URL the chat app will call for auth,
        // sessions, etc. Silently defaulting to "https://" + hostname (the
        // chat's OWN domain) used to produce a stack that 404'd on every login
        // request. Require it from the caller — the frontend already knows its
        // backend URL and sends it.
        if (req.apiOrigin() == null || req.apiOrigin().isBlank()) {
            throw new IllegalArgumentException(
                    "apiOrigin is required — pass the ClawOps backend's public URL "
                            + "(e.g. https://clawops.example.com). The frontend pre-fills this; "
                            + "direct API callers must include it explicitly.");
        }
        String apiOrigin = req.apiOrigin();

        long started = System.currentTimeMillis();

        // 1. Tear down any host-nginx openclaw-managed config for this hostname
        // so the chat sidecar can own port 80/443 without stale leftovers.
        try {
            nginxConfigService.removeHostManagedConfigIfPresent(server, hostname);
        } catch (Exception e) {
            // Non-fatal: bootstrap.sh will stop host nginx anyway.
            log.warn("Failed to pre-clean host-nginx config on '{}': {}", server.getName(), e.getMessage());
        }

        // 2. Build and run the bootstrap pipeline. sudo -E preserves every
        // env var we set on the same line.
        String cmd = new StringBuilder()
                .append("curl -fsSL ").append(shellQuote(installerProps.getBootstrapUrl()))
                .append(" | sudo -E ")
                .append("INSTALLER_REPO=").append(shellQuote(installerProps.getRepo())).append(' ')
                .append("INSTALLER_REF=").append(shellQuote(installerProps.getRef())).append(' ')
                .append("HOSTNAME=").append(shellQuote(hostname)).append(' ')
                .append("ALLOWED_EMAIL=").append(shellQuote(req.allowedEmail())).append(' ')
                .append("NEXT_PUBLIC_API_ORIGIN=").append(shellQuote(apiOrigin)).append(' ')
                .append("ALLOWED_ORIGINS=").append(shellQuote("https://" + hostname)).append(' ')
                .append("bash 2>&1")
                .toString();

        CommandResult res = sshService.executeCommand(server, cmd, INSTALL_TIMEOUT_SECONDS);
        long durationMs = System.currentTimeMillis() - started;

        String combined = ((res.stdout() == null ? "" : res.stdout())
                + (res.stderr() == null || res.stderr().isBlank() ? "" : "\n[stderr]\n" + res.stderr()));
        String truncated = truncate(combined);

        try {
            if (res.exitCode() == 0) {
                // Chat sidecar now owns 80/443 — update the SSL cert's ownership flag
                // so future probes / re-provisioning operate in co-existence mode.
                sslCertificateRepository.findByServerId(serverId).ifPresent(cert -> {
                    if (cert.isHostNginxManaged()) {
                        cert.setHostNginxManaged(false);
                        sslCertificateRepository.save(cert);
                        log.info("Flipped SslCertificate.hostNginxManaged=false for server '{}'", server.getName());
                    }
                });
                auditService.log(AuditAction.APP_INSTALLED, "CHAT_APP", serverId, userId,
                        "Chat app installed on '%s' (%s)".formatted(server.getName(), hostname));
            } else {
                auditService.log(AuditAction.APP_INSTALL_FAILED, "CHAT_APP", serverId, userId,
                        "Chat app install failed on '%s' with exit %d".formatted(server.getName(), res.exitCode()));
            }
        } catch (Exception ignored) { /* audit is best-effort */ }

        return new ChatInstallResult(res.exitCode(), truncated, durationMs);
    }

    /**
     * Pull the latest chat image and recreate the container.
     * Leaves .env + nginx config + cert mount intact.
     */
    public ChatInstallResult updateChatApp(UUID serverId, UUID userId) {
        Server server = serverService.getServerEntity(serverId);
        long started = System.currentTimeMillis();

        String cmd = "cd /opt/claw-chat && "
                + "sudo docker compose pull 2>&1 && "
                + "sudo docker compose up -d --remove-orphans 2>&1";

        CommandResult res = sshService.executeCommand(server, cmd, UPDATE_TIMEOUT_SECONDS);
        long durationMs = System.currentTimeMillis() - started;

        String combined = ((res.stdout() == null ? "" : res.stdout())
                + (res.stderr() == null || res.stderr().isBlank() ? "" : "\n[stderr]\n" + res.stderr()));
        String truncated = truncate(combined);

        try {
            if (res.exitCode() == 0) {
                auditService.log(AuditAction.APP_UPDATED, "CHAT_APP", serverId, userId,
                        "Chat app updated on '%s'".formatted(server.getName()));
            } else {
                auditService.log(AuditAction.APP_UPDATE_FAILED, "CHAT_APP", serverId, userId,
                        "Chat app update failed on '%s' with exit %d".formatted(server.getName(), res.exitCode()));
            }
        } catch (Exception ignored) { /* audit is best-effort */ }

        return new ChatInstallResult(res.exitCode(), truncated, durationMs);
    }

    /**
     * Stop and remove the chat containers + wipe {@code /opt/claw-chat}. SSL cert,
     * DNS record, and server itself are untouched — this is an app-level uninstall.
     */
    public ChatInstallResult uninstallChatApp(UUID serverId, UUID userId) {
        Server server = serverService.getServerEntity(serverId);
        long started = System.currentTimeMillis();

        // Best-effort teardown: each command tolerates missing pieces so a partial
        // install (no compose file, no containers, etc.) still gets cleaned up.
        String cmd = "set +e; "
                + "if [ -f /opt/claw-chat/docker-compose.yml ]; then "
                + "  cd /opt/claw-chat && sudo docker compose down -v 2>&1; "
                + "fi; "
                + "sudo docker rm -f claw-chat claw-nginx 2>/dev/null; "
                + "sudo rm -rf /opt/claw-chat; "
                + "echo 'uninstall complete'";

        CommandResult res = sshService.executeCommand(server, cmd, UNINSTALL_TIMEOUT_SECONDS);
        long durationMs = System.currentTimeMillis() - started;

        String combined = ((res.stdout() == null ? "" : res.stdout())
                + (res.stderr() == null || res.stderr().isBlank() ? "" : "\n[stderr]\n" + res.stderr()));
        String truncated = truncate(combined);

        try {
            if (res.exitCode() == 0) {
                auditService.log(AuditAction.APP_UNINSTALLED, "CHAT_APP", serverId, userId,
                        "Chat app uninstalled on '%s'".formatted(server.getName()));
            } else {
                auditService.log(AuditAction.APP_UNINSTALL_FAILED, "CHAT_APP", serverId, userId,
                        "Chat app uninstall failed on '%s' with exit %d".formatted(server.getName(), res.exitCode()));
            }
        } catch (Exception ignored) { /* audit is best-effort */ }

        return new ChatInstallResult(res.exitCode(), truncated, durationMs);
    }

    /**
     * Probe {@code docker ps} for the {@code claw-chat} container.
     */
    public ChatAppStatus getChatStatus(UUID serverId) {
        Server server = serverService.getServerEntity(serverId);
        CommandResult r = sshService.executeCommand(server,
                "docker ps -a --filter 'name=^claw-chat$' --format '{{.Status}}' 2>/dev/null || true",
                STATUS_PROBE_TIMEOUT_SECONDS);
        String out = r.stdout() == null ? "" : r.stdout().trim();
        if (out.isEmpty()) {
            return ChatAppStatus.notInstalled();
        }
        boolean running = out.startsWith("Up");
        String health = parseHealth(out);
        return new ChatAppStatus(true, running, health, out);
    }

    /* ---------------------------------------------------------------- */
    /*  Internals                                                        */
    /* ---------------------------------------------------------------- */

    private static String resolveInstallHostname(Server server) {
        // Prefer the server's subdomain + rootDomain (the SSL-provisioned hostname).
        String rootDomain = server.getRootDomain();
        String subdomain = server.getSubdomain();
        if (rootDomain != null && !rootDomain.isBlank()) {
            if (subdomain != null && !subdomain.isBlank()) {
                return subdomain + "." + rootDomain;
            }
            return rootDomain;
        }
        // Fallback — install still works, but without a real domain HTTPS won't validate.
        String fallback = server.getHostname();
        if (fallback == null || fallback.isBlank()) fallback = server.getIpAddress();
        if (fallback == null || fallback.isBlank()) {
            throw new IllegalStateException("Server has no hostname / IP / assigned domain to install against");
        }
        return fallback;
    }

    private static String parseHealth(String dockerStatus) {
        // e.g. "Up 3 minutes (healthy)" or "Up 12 seconds (health: starting)"
        int i = dockerStatus.indexOf('(');
        if (i < 0) return "none";
        int j = dockerStatus.indexOf(')', i);
        if (j < 0) return "none";
        String inner = dockerStatus.substring(i + 1, j).trim();
        if (inner.startsWith("health:")) inner = inner.substring("health:".length()).trim();
        return inner.isEmpty() ? "none" : inner;
    }

    private static String shellQuote(String raw) {
        if (raw == null) return "''";
        return "'" + raw.replace("'", "'\\''") + "'";
    }

    private static String truncate(String s) {
        if (s == null) return "";
        byte[] utf = s.getBytes(StandardCharsets.UTF_8);
        if (utf.length <= MAX_OUTPUT_BYTES) return s;
        return new String(utf, 0, MAX_OUTPUT_BYTES, StandardCharsets.UTF_8)
                + "\n\n… output truncated at " + MAX_OUTPUT_BYTES + " bytes";
    }
}
