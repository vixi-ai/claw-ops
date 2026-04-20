package com.openclaw.manager.openclawserversmanager.apps.service;

import com.openclaw.manager.openclawserversmanager.apps.dto.ChatAppStatus;
import com.openclaw.manager.openclawserversmanager.apps.dto.ChatInstallRequest;
import com.openclaw.manager.openclawserversmanager.apps.dto.ChatInstallResult;
import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.service.ServerService;
import com.openclaw.manager.openclawserversmanager.ssh.model.CommandResult;
import com.openclaw.manager.openclawserversmanager.ssh.service.SshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Deploys bundled third-party apps (currently just "claw-chat") onto managed servers by
 * uploading the install artifacts from the classpath and running the installer script
 * over SSH. Status is probed by inspecting the container via {@code docker ps}.
 */
@Service
public class AppInstallService {

    private static final Logger log = LoggerFactory.getLogger(AppInstallService.class);
    private static final String REMOTE_STAGING_DIR = "/tmp/claw-chat-install";
    private static final String REMOTE_NGINX_DIR = REMOTE_STAGING_DIR + "/nginx";
    private static final int INSTALL_TIMEOUT_SECONDS = 600;
    private static final int STATUS_PROBE_TIMEOUT_SECONDS = 15;
    private static final int MAX_OUTPUT_BYTES = 16 * 1024;

    private final ServerService serverService;
    private final SshService sshService;
    private final AuditService auditService;

    public AppInstallService(ServerService serverService,
                             SshService sshService,
                             AuditService auditService) {
        this.serverService = serverService;
        this.sshService = sshService;
        this.auditService = auditService;
    }

    /**
     * Upload the installer bundle + run it on the server. Returns the install's exit
     * code + truncated output so the frontend can render the log.
     */
    public ChatInstallResult installChatApp(UUID serverId, ChatInstallRequest req, UUID userId) {
        Server server = serverService.getServerEntity(serverId);
        if (server.getHostname() == null && server.getIpAddress() == null) {
            throw new ResourceNotFoundException("Server has no hostname or IP configured");
        }
        String hostname = resolveInstallHostname(server);
        String apiOrigin = (req.apiOrigin() != null && !req.apiOrigin().isBlank())
                ? req.apiOrigin()
                : "https://" + hostname;

        long started = System.currentTimeMillis();

        // 1. Upload installer files via SFTP. All three are tiny text artifacts.
        try {
            uploadResource(server, "apps/chat/install.sh", REMOTE_STAGING_DIR + "/install.sh");
            uploadResource(server, "apps/chat/http-only.conf", REMOTE_NGINX_DIR + "/http-only.conf");
            uploadResource(server, "apps/chat/https.conf", REMOTE_NGINX_DIR + "/https.conf");
        } catch (IOException e) {
            log.error("Failed to upload chat installer to '{}': {}", server.getName(), e.getMessage(), e);
            return new ChatInstallResult(-1,
                    "Failed to upload installer: " + e.getMessage(),
                    System.currentTimeMillis() - started);
        }

        // 2. Run install.sh with env vars. sudo -E preserves our vars across the sudo boundary.
        String cmd = new StringBuilder()
                .append("chmod +x ").append(REMOTE_STAGING_DIR).append("/install.sh && ")
                .append("cd ").append(REMOTE_STAGING_DIR).append(" && ")
                .append("sudo -E ")
                .append("HOSTNAME=").append(shellQuote(hostname)).append(' ')
                .append("ALLOWED_EMAIL=").append(shellQuote(req.allowedEmail())).append(' ')
                .append("NEXT_PUBLIC_API_ORIGIN=").append(shellQuote(apiOrigin)).append(' ')
                .append("ALLOWED_ORIGINS=").append(shellQuote("https://" + hostname)).append(' ')
                .append("bash install.sh 2>&1")
                .toString();

        CommandResult res = sshService.executeCommand(server, cmd, INSTALL_TIMEOUT_SECONDS);
        long durationMs = System.currentTimeMillis() - started;

        String combined = ((res.stdout() == null ? "" : res.stdout())
                + (res.stderr() == null || res.stderr().isBlank() ? "" : "\n[stderr]\n" + res.stderr()));
        String truncated = truncate(combined);

        try {
            if (res.exitCode() == 0) {
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

    private void uploadResource(Server server, String classpath, String remotePath) throws IOException {
        ClassPathResource res = new ClassPathResource(classpath);
        try (var in = res.getInputStream()) {
            byte[] bytes = in.readAllBytes();
            sshService.uploadFile(server, bytes, remotePath);
        }
    }

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
