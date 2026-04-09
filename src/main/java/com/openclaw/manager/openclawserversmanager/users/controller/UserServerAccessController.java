package com.openclaw.manager.openclawserversmanager.users.controller;

import com.openclaw.manager.openclawserversmanager.users.dto.ServerAccessRequest;
import com.openclaw.manager.openclawserversmanager.users.dto.ServerAccessResponse;
import com.openclaw.manager.openclawserversmanager.users.service.UserServerAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}/server-access")
@Tag(name = "User Server Access", description = "Manage per-server access for EMPLOYEE users")
@SecurityRequirement(name = "bearerAuth")
public class UserServerAccessController {

    private final UserServerAccessService accessService;

    public UserServerAccessController(UserServerAccessService accessService) {
        this.accessService = accessService;
    }

    @GetMapping
    @Operation(summary = "List servers assigned to a user")
    public ResponseEntity<List<ServerAccessResponse>> getServerAccess(@PathVariable UUID userId) {
        return ResponseEntity.ok(accessService.getServerAccessForUser(userId));
    }

    @PostMapping
    @Operation(summary = "Assign servers to an EMPLOYEE user")
    public ResponseEntity<List<ServerAccessResponse>> assignServers(
            @PathVariable UUID userId,
            @Valid @RequestBody ServerAccessRequest request,
            Authentication authentication) {
        UUID assignedBy = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(201).body(accessService.assignServers(userId, request.serverIds(), assignedBy));
    }

    @DeleteMapping("/{serverId}")
    @Operation(summary = "Revoke server access for a user")
    public ResponseEntity<Void> revokeServerAccess(
            @PathVariable UUID userId,
            @PathVariable UUID serverId,
            Authentication authentication) {
        UUID currentUserId = (UUID) authentication.getPrincipal();
        accessService.revokeServer(userId, serverId, currentUserId);
        return ResponseEntity.noContent().build();
    }
}
