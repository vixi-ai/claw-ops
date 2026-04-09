package com.openclaw.manager.openclawserversmanager.notifications.controller;

import com.openclaw.manager.openclawserversmanager.notifications.dto.CreateNotificationProviderRequest;
import com.openclaw.manager.openclawserversmanager.notifications.dto.NotificationProviderResponse;
import com.openclaw.manager.openclawserversmanager.notifications.dto.UpdateNotificationProviderRequest;
import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProvider;
import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProviderType;
import com.openclaw.manager.openclawserversmanager.notifications.service.FirebaseService;
import com.openclaw.manager.openclawserversmanager.notifications.service.NotificationProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notification-providers")
@Tag(name = "Notification Providers", description = "Manage notification delivery providers")
@SecurityRequirement(name = "bearerAuth")
public class NotificationProviderController {

    private final NotificationProviderService providerService;
    private final FirebaseService firebaseService;

    public NotificationProviderController(NotificationProviderService providerService,
                                          FirebaseService firebaseService) {
        this.providerService = providerService;
        this.firebaseService = firebaseService;
    }

    @PostMapping
    @Operation(summary = "Create a notification provider")
    public ResponseEntity<NotificationProviderResponse> createProvider(
            @Valid @RequestBody CreateNotificationProviderRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(providerService.createProvider(request, userId));
    }

    @GetMapping
    @Operation(summary = "List all notification providers")
    public ResponseEntity<Page<NotificationProviderResponse>> getAllProviders(Pageable pageable) {
        return ResponseEntity.ok(providerService.getAllProviders(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get notification provider by ID")
    public ResponseEntity<NotificationProviderResponse> getProviderById(@PathVariable UUID id) {
        return ResponseEntity.ok(providerService.getProviderById(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a notification provider")
    public ResponseEntity<NotificationProviderResponse> updateProvider(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateNotificationProviderRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(providerService.updateProvider(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a notification provider")
    public ResponseEntity<Void> deleteProvider(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        providerService.deleteProvider(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "Validate a notification provider's credentials")
    public ResponseEntity<Map<String, Object>> validateProvider(@PathVariable UUID id) {
        NotificationProvider provider = providerService.getProviderEntity(id);
        try {
            if (provider.getProviderType() == NotificationProviderType.FCM) {
                String projectId = firebaseService.validateCredentials(provider);
                return ResponseEntity.ok(Map.of("valid", true, "message",
                        "Firebase credentials valid (project: %s)".formatted(projectId)));
            }
            return ResponseEntity.ok(Map.of("valid", true, "message", "Provider type does not require validation"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("valid", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/set-default")
    @Operation(summary = "Set a notification provider as the default")
    public ResponseEntity<NotificationProviderResponse> setDefault(
            @PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(providerService.setDefault(id, userId));
    }
}
