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
import com.openclaw.manager.openclawserversmanager.ssh.service.FileEntry;
import com.openclaw.manager.openclawserversmanager.ssh.service.SshService;
import com.openclaw.manager.openclawserversmanager.terminal.service.TerminalSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/servers")
@Tag(name = "Servers", description = "Server inventory and connection registry")
@SecurityRequirement(name = "bearerAuth")
public class ServerController {

    private final ServerService serverService;
    private final SshService sshService;
    private final TerminalSessionService terminalSessionService;
    private final AuditService auditService;

    public ServerController(ServerService serverService,
                            SshService sshService,
                            TerminalSessionService terminalSessionService,
                            AuditService auditService) {
        this.serverService = serverService;
        this.sshService = sshService;
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
    public ResponseEntity<Page<ServerResponse>> getAllServers(Pageable pageable, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        String role = extractRole(authentication);
        return ResponseEntity.ok(serverService.getAllServers(pageable, userId, role));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get server details")
    public ResponseEntity<ServerResponse> getServerById(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        String role = extractRole(authentication);
        return ResponseEntity.ok(serverService.getServerById(id, userId, role));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a server")
    public ResponseEntity<ServerResponse> updateServer(@PathVariable UUID id,
                                                       @Valid @RequestBody UpdateServerRequest request,
                                                       Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        String role = extractRole(authentication);
        serverService.checkServerAccess(id, userId, role);
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
        serverService.checkServerAccess(id, userId, extractRole(authentication));
        return ResponseEntity.ok(serverService.testConnection(id, userId));
    }

    @PostMapping("/{id}/ssh/command")
    @Operation(summary = "Execute a command on a remote server via SSH")
    public ResponseEntity<CommandResponse> executeCommand(@PathVariable UUID id,
                                                          @Valid @RequestBody ExecuteCommandRequest request,
                                                          Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        serverService.checkServerAccess(id, userId, extractRole(authentication));
        return ResponseEntity.ok(serverService.executeCommand(id, request.command(), request.timeoutSeconds(), userId));
    }

    @GetMapping("/{id}/sftp/ls")
    @Operation(summary = "List files and directories on a remote server via SFTP")
    public ResponseEntity<List<FileEntry>> listDirectory(@PathVariable UUID id,
                                                          @RequestParam(defaultValue = "/") String path,
                                                          Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        serverService.checkServerAccess(id, userId, extractRole(authentication));
        var server = serverService.getServerEntity(id);
        return ResponseEntity.ok(sshService.listDirectory(server, path));
    }

    @PostMapping("/{id}/sftp/upload")
    @Operation(summary = "Upload a file to a remote server via SFTP")
    public ResponseEntity<Map<String, String>> uploadFile(@PathVariable UUID id,
                                                          @RequestParam String path,
                                                          @RequestParam("file") MultipartFile file,
                                                          Authentication authentication) throws IOException {
        UUID userId = (UUID) authentication.getPrincipal();
        serverService.checkServerAccess(id, userId, extractRole(authentication));
        var server = serverService.getServerEntity(id);
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            fileName = "upload";
        }
        String remotePath = path.endsWith("/") ? path + fileName : path + "/" + fileName;
        sshService.uploadFile(server, file.getBytes(), remotePath);
        return ResponseEntity.ok(Map.of("fileName", fileName, "path", remotePath));
    }

    @GetMapping("/{id}/sftp/download")
    @Operation(summary = "Download a file from a remote server via SFTP (streamed)")
    public void downloadFile(@PathVariable UUID id,
                             @RequestParam String path,
                             Authentication authentication,
                             jakarta.servlet.http.HttpServletResponse response) throws IOException {
        UUID userId = (UUID) authentication.getPrincipal();
        serverService.checkServerAccess(id, userId, extractRole(authentication));
        var server = serverService.getServerEntity(id);
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition",
                ContentDisposition.attachment().filename(fileName).build().toString());
        sshService.downloadFileStreaming(server, path, response.getOutputStream());
        response.flushBuffer();
    }

    @GetMapping("/{id}/ssh/session-token")
    @Operation(summary = "Generate a one-time session token for WebSocket terminal")
    public ResponseEntity<Map<String, Object>> getSessionToken(@PathVariable UUID id,
                                                                Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        serverService.checkServerAccess(id, userId, extractRole(authentication));
        serverService.getServerById(id);

        String token = terminalSessionService.generateSessionToken(id, userId);

        try {
            auditService.log(AuditAction.TERMINAL_SESSION_REQUESTED, "SERVER", id, userId,
                    "Terminal session token requested");
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok(Map.of("token", token, "expiresIn", 60));
    }

    private String extractRole(Authentication authentication) {
        return authentication.getAuthorities().iterator().next()
                .getAuthority().replace("ROLE_", "");
    }
}
