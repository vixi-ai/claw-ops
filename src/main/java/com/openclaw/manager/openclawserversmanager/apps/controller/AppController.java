package com.openclaw.manager.openclawserversmanager.apps.controller;

import com.openclaw.manager.openclawserversmanager.apps.dto.ChatAppStatus;
import com.openclaw.manager.openclawserversmanager.apps.dto.ChatInstallRequest;
import com.openclaw.manager.openclawserversmanager.apps.dto.ChatInstallResult;
import com.openclaw.manager.openclawserversmanager.apps.service.AppInstallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoints for managing optional apps on a server. v1 exposes only
 * the claw-chat app; the URL shape leaves room for more ({@code /apps/{appId}/...}).
 */
@RestController
@RequestMapping("/api/v1/servers/{serverId}/apps")
@Tag(name = "Server Apps", description = "Optional apps installable on managed servers")
@SecurityRequirement(name = "bearerAuth")
public class AppController {

    private final AppInstallService appInstallService;

    public AppController(AppInstallService appInstallService) {
        this.appInstallService = appInstallService;
    }

    @GetMapping("/chat/status")
    @Operation(summary = "Check whether claw-chat is installed + running on this server")
    public ResponseEntity<ChatAppStatus> status(@PathVariable UUID serverId) {
        return ResponseEntity.ok(appInstallService.getChatStatus(serverId));
    }

    @PostMapping("/chat/install")
    @Operation(summary = "Install claw-chat on this server (idempotent — safe to re-run)")
    public ResponseEntity<ChatInstallResult> install(@PathVariable UUID serverId,
                                                     @Valid @RequestBody ChatInstallRequest body,
                                                     Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        ChatInstallResult result = appInstallService.installChatApp(serverId, body, userId);
        // Return 200 regardless of install exit code so the frontend can render the log;
        // exitCode in the body signals real success/failure.
        return ResponseEntity.ok(result);
    }
}
