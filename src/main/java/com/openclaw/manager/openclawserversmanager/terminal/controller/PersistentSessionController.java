package com.openclaw.manager.openclawserversmanager.terminal.controller;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.service.ServerService;
import com.openclaw.manager.openclawserversmanager.ssh.model.SshSession;
import com.openclaw.manager.openclawserversmanager.ssh.service.SshService;
import com.openclaw.manager.openclawserversmanager.terminal.model.TerminalSession;
import com.openclaw.manager.openclawserversmanager.terminal.service.TerminalSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/servers/{serverId}/persistent-sessions")
@Tag(name = "Persistent Sessions", description = "Long-lived terminal sessions that survive browser disconnects")
@SecurityRequirement(name = "bearerAuth")
public class PersistentSessionController {

    private static final Logger log = LoggerFactory.getLogger(PersistentSessionController.class);

    private final ServerService serverService;
    private final SshService sshService;
    private final TerminalSessionService terminalSessionService;
    private final AuditService auditService;

    public PersistentSessionController(ServerService serverService,
                                       SshService sshService,
                                       TerminalSessionService terminalSessionService,
                                       AuditService auditService) {
        this.serverService = serverService;
        this.sshService = sshService;
        this.terminalSessionService = terminalSessionService;
        this.auditService = auditService;
    }

    @PostMapping
    @Operation(summary = "Create a persistent terminal session")
    public ResponseEntity<Map<String, Object>> createSession(
            @PathVariable UUID serverId,
            @RequestParam(defaultValue = "120") int cols,
            @RequestParam(defaultValue = "40") int rows,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();

        if (!terminalSessionService.canOpenSession(userId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Maximum session limit reached"));
        }

        Server server;
        try {
            server = serverService.getServerEntity(serverId);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }

        SshSession sshSession;
        try {
            sshSession = sshService.openInteractiveSession(server, cols, rows);
        } catch (Exception e) {
            log.error("Failed to open persistent SSH session to server {}: {}", server.getName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Failed to connect: " + e.getMessage()));
        }

        TerminalSession terminalSession = new TerminalSession(
                sshSession.getSessionId(), serverId, userId, sshSession, null, true);
        terminalSessionService.registerSession(terminalSession.getSessionId(), terminalSession);

        String token = terminalSessionService.generatePersistentToken(serverId, userId, terminalSession.getSessionId());

        try {
            auditService.log(AuditAction.TERMINAL_SESSION_OPENED, "SERVER", serverId, userId,
                    "Persistent terminal session created on '%s'".formatted(server.getName()));
        } catch (Exception ignored) {
        }

        log.info("Persistent session created: {} for server {}", terminalSession.getSessionId(), server.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "sessionId", terminalSession.getSessionId(),
                "token", token,
                "expiresIn", 60
        ));
    }

    @GetMapping
    @Operation(summary = "List active persistent sessions for a server")
    public ResponseEntity<List<Map<String, Object>>> listSessions(
            @PathVariable UUID serverId,
            Authentication authentication) {

        // Verify server exists
        serverService.getServerById(serverId);

        List<Map<String, Object>> sessions = terminalSessionService.findPersistentSessions(serverId).stream()
                .map(s -> Map.<String, Object>of(
                        "sessionId", s.getSessionId(),
                        "createdAt", s.getCreatedAt().toString(),
                        "lastActivityAt", s.getLastActivityAt().toString(),
                        "connected", s.getSshSession().isConnected()
                ))
                .toList();

        return ResponseEntity.ok(sessions);
    }

    @PostMapping("/{sessionId}/token")
    @Operation(summary = "Get a reconnection token for an existing persistent session")
    public ResponseEntity<Map<String, Object>> getReconnectionToken(
            @PathVariable UUID serverId,
            @PathVariable String sessionId,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();

        TerminalSession session = terminalSessionService.getSession(sessionId);
        if (session == null || !session.isPersistentSession() || !session.getServerId().equals(serverId)) {
            return ResponseEntity.notFound().build();
        }

        if (!session.getSshSession().isConnected()) {
            terminalSessionService.removeSession(sessionId);
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(Map.of("error", "Session SSH connection lost"));
        }

        String token = terminalSessionService.generatePersistentToken(serverId, userId, sessionId);
        return ResponseEntity.ok(Map.of("token", token, "expiresIn", 60));
    }

    @PostMapping("/{sessionId}/kill")
    @Operation(summary = "Kill a persistent terminal session")
    public ResponseEntity<Void> killSession(
            @PathVariable UUID serverId,
            @PathVariable String sessionId,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();

        TerminalSession session = terminalSessionService.getSession(sessionId);
        if (session == null || !session.isPersistentSession() || !session.getServerId().equals(serverId)) {
            return ResponseEntity.notFound().build();
        }

        terminalSessionService.removeSession(sessionId);
        try {
            session.getSshSession().close();
        } catch (IOException e) {
            log.debug("Error closing persistent SSH session: {}", e.getMessage());
        }

        try {
            auditService.log(AuditAction.TERMINAL_SESSION_CLOSED, "SERVER", serverId, userId,
                    "Persistent terminal session killed");
        } catch (Exception ignored) {
        }

        return ResponseEntity.noContent().build();
    }
}
