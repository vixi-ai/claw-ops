package com.openclaw.manager.openclawserversmanager.servers.controller;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.servers.dto.CreateServerRequest;
import com.openclaw.manager.openclawserversmanager.servers.dto.ServerResponse;
import com.openclaw.manager.openclawserversmanager.servers.dto.TestConnectionResponse;
import com.openclaw.manager.openclawserversmanager.servers.dto.UpdateServerRequest;
import com.openclaw.manager.openclawserversmanager.servers.service.ServerService;
import com.openclaw.manager.openclawserversmanager.ssh.dto.CommandResponse;
import com.openclaw.manager.openclawserversmanager.ssh.dto.ExecuteCommandRequest;
import com.openclaw.manager.openclawserversmanager.terminal.service.TerminalSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/servers")
@Tag(name = "Servers", description = "Server inventory and connection registry")
@SecurityRequirement(name = "bearerAuth")
public class ServerController {

    private final ServerService serverService;
    private final TerminalSessionService terminalSessionService;
    private final AuditService auditService;

    public ServerController(ServerService serverService,
                            TerminalSessionService terminalSessionService,
                            AuditService auditService) {
        this.serverService = serverService;
        this.terminalSessionService = terminalSessionService;
        this.auditService = auditService;
    }

    @PostMapping
    @Operation(summary = "Register a new server")
    public ResponseEntity<ServerResponse> createServer(@Valid @RequestBody CreateServerRequest request,
                                                       Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(serverService.createServer(request, userId));
    }

    @GetMapping
    @Operation(summary = "List all servers (paginated)")
    public ResponseEntity<Page<ServerResponse>> getAllServers(Pageable pageable) {
        return ResponseEntity.ok(serverService.getAllServers(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get server details")
    public ResponseEntity<ServerResponse> getServerById(@PathVariable UUID id) {
        return ResponseEntity.ok(serverService.getServerById(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a server")
    public ResponseEntity<ServerResponse> updateServer(@PathVariable UUID id,
                                                       @Valid @RequestBody UpdateServerRequest request,
                                                       Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(serverService.updateServer(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a server (ADMIN only)")
    public ResponseEntity<Void> deleteServer(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        serverService.deleteServer(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test-connection")
    @Operation(summary = "Test SSH connection to server")
    public ResponseEntity<TestConnectionResponse> testConnection(@PathVariable UUID id,
                                                                  Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(serverService.testConnection(id, userId));
    }

    @PostMapping("/{id}/ssh/command")
    @Operation(summary = "Execute a command on a remote server via SSH")
    public ResponseEntity<CommandResponse> executeCommand(@PathVariable UUID id,
                                                          @Valid @RequestBody ExecuteCommandRequest request,
                                                          Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(serverService.executeCommand(id, request.command(), request.timeoutSeconds(), userId));
    }

    @GetMapping("/{id}/ssh/session-token")
    @Operation(summary = "Generate a one-time session token for WebSocket terminal")
    public ResponseEntity<Map<String, Object>> getSessionToken(@PathVariable UUID id,
                                                                Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        // Verify server exists
        serverService.getServerById(id);

        String token = terminalSessionService.generateSessionToken(id, userId);

        try {
            auditService.log(AuditAction.TERMINAL_SESSION_REQUESTED, "SERVER", id, userId,
                    "Terminal session token requested");
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok(Map.of("token", token, "expiresIn", 60));
    }
}
