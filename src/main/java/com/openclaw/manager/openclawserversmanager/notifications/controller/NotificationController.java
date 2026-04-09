package com.openclaw.manager.openclawserversmanager.notifications.controller;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.notifications.dto.FcmSubscribeRequest;
import com.openclaw.manager.openclawserversmanager.notifications.dto.FcmUnsubscribeRequest;
import com.openclaw.manager.openclawserversmanager.notifications.dto.PushSubscribeRequest;
import com.openclaw.manager.openclawserversmanager.notifications.dto.RegisterDeviceRequest;
import com.openclaw.manager.openclawserversmanager.notifications.dto.SendNotificationRequest;
import com.openclaw.manager.openclawserversmanager.notifications.dto.UserDeviceResponse;
import com.openclaw.manager.openclawserversmanager.notifications.entity.DeviceToken;
import com.openclaw.manager.openclawserversmanager.notifications.entity.PushSubscription;
import com.openclaw.manager.openclawserversmanager.notifications.service.FirebaseService;
import com.openclaw.manager.openclawserversmanager.notifications.service.NotificationDispatchService;
import com.openclaw.manager.openclawserversmanager.notifications.service.NotificationProviderService;
import com.openclaw.manager.openclawserversmanager.notifications.service.UserDeviceService;
import com.openclaw.manager.openclawserversmanager.notifications.service.WebPushService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Push notification subscriptions and sending")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final WebPushService webPushService;
    private final FirebaseService firebaseService;
    private final NotificationDispatchService dispatchService;
    private final NotificationProviderService providerService;
    private final UserDeviceService userDeviceService;
    private final AuditService auditService;

    public NotificationController(WebPushService webPushService,
                                  FirebaseService firebaseService,
                                  NotificationDispatchService dispatchService,
                                  NotificationProviderService providerService,
                                  UserDeviceService userDeviceService,
                                  AuditService auditService) {
        this.webPushService = webPushService;
        this.firebaseService = firebaseService;
        this.dispatchService = dispatchService;
        this.providerService = providerService;
        this.userDeviceService = userDeviceService;
        this.auditService = auditService;
    }

    // ── Web Push endpoints ──

    @GetMapping("/vapid-key")
    @Operation(summary = "Get the VAPID public key for push subscription")
    public ResponseEntity<Map<String, String>> getVapidPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", webPushService.getVapidPublicKey()));
    }

    @GetMapping("/fcm-config")
    @Operation(summary = "Get the Firebase web config for client-side FCM initialization")
    public ResponseEntity<Map<String, Object>> getFcmConfig() {
        try {
            var provider = providerService.getDefaultFcmProvider();
            var settings = com.openclaw.manager.openclawserversmanager.notifications.mapper.NotificationProviderMapper
                    .deserializeSettings(provider.getProviderSettings());
            if (settings == null || !settings.containsKey("firebaseConfig")) {
                return ResponseEntity.notFound().build();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) settings.get("firebaseConfig");
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/push/subscribe")
    @Operation(summary = "Subscribe a browser to push notifications")
    public ResponseEntity<Map<String, Object>> subscribe(
            @Valid @RequestBody PushSubscribeRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        PushSubscription sub = webPushService.subscribe(
                request.endpoint(), request.keyAuth(), request.keyP256dh(), userId);
        return ResponseEntity.ok(Map.of("id", sub.getId(), "endpoint", sub.getEndpoint()));
    }

    @DeleteMapping("/push/unsubscribe")
    @Operation(summary = "Unsubscribe a browser from push notifications")
    public ResponseEntity<Void> unsubscribe(@RequestBody Map<String, String> body) {
        webPushService.unsubscribe(body.get("endpoint"));
        return ResponseEntity.noContent().build();
    }

    // ── FCM endpoints ──

    @PostMapping("/fcm/subscribe")
    @Operation(summary = "Register an FCM device token")
    public ResponseEntity<Map<String, Object>> fcmSubscribe(
            @Valid @RequestBody FcmSubscribeRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        DeviceToken dt = firebaseService.subscribe(request.token(), request.platform(), userId);
        return ResponseEntity.ok(Map.of("id", dt.getId(), "token", dt.getToken()));
    }

    @DeleteMapping("/fcm/unsubscribe")
    @Operation(summary = "Unregister an FCM device token")
    public ResponseEntity<Void> fcmUnsubscribe(@Valid @RequestBody FcmUnsubscribeRequest request) {
        firebaseService.unsubscribe(request.token());
        return ResponseEntity.noContent().build();
    }

    // ── User Devices ──

    @PostMapping("/devices")
    @Operation(summary = "Register the current device for notifications")
    public ResponseEntity<UserDeviceResponse> registerDevice(
            @Valid @RequestBody RegisterDeviceRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(201).body(userDeviceService.registerDevice(request, userId));
    }

    @GetMapping("/devices")
    @Operation(summary = "List all devices for the current user")
    public ResponseEntity<List<UserDeviceResponse>> getMyDevices(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(userDeviceService.getUserDevices(userId));
    }

    @PatchMapping("/devices/{id}/toggle")
    @Operation(summary = "Enable or disable notifications for a device")
    public ResponseEntity<UserDeviceResponse> toggleDeviceNotifications(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        boolean enabled = body.getOrDefault("enabled", true);
        return ResponseEntity.ok(userDeviceService.toggleNotifications(id, enabled, userId));
    }

    @DeleteMapping("/devices/{id}")
    @Operation(summary = "Remove a device and its notification subscription")
    public ResponseEntity<Void> removeDevice(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        userDeviceService.removeDevice(id, userId);
        return ResponseEntity.noContent().build();
    }

    // ── Send ──

    @PostMapping("/send")
    @Operation(summary = "Send a notification to all subscribers of the default provider")
    public ResponseEntity<Map<String, Object>> sendNotification(
            @Valid @RequestBody SendNotificationRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        int sent = dispatchService.sendToDefault(request.title(), request.body());

        try {
            auditService.log(AuditAction.NOTIFICATION_SENT, "NOTIFICATION", null, userId,
                    "Notification sent to %d recipients via default provider: '%s'".formatted(sent, request.title()));
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok(Map.of("sent", sent, "title", request.title()));
    }

    @PostMapping("/send/all")
    @Operation(summary = "Send a notification to all subscribers across ALL enabled providers")
    public ResponseEntity<Map<String, Object>> sendToAll(
            @Valid @RequestBody SendNotificationRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        int sent = dispatchService.sendToAll(request.title(), request.body());

        try {
            auditService.log(AuditAction.NOTIFICATION_SENT, "NOTIFICATION", null, userId,
                    "Broadcast notification sent to %d recipients: '%s'".formatted(sent, request.title()));
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok(Map.of("sent", sent, "title", request.title()));
    }

    @PostMapping("/send/user/{targetUserId}")
    @Operation(summary = "Send a notification to all devices of a specific user")
    public ResponseEntity<Map<String, Object>> sendToUser(
            @PathVariable UUID targetUserId,
            @Valid @RequestBody SendNotificationRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        int sent = dispatchService.sendToUser(targetUserId, request.title(), request.body());

        try {
            auditService.log(AuditAction.NOTIFICATION_SENT, "NOTIFICATION", null, userId,
                    "Notification sent to user %s (%d devices): '%s'".formatted(targetUserId, sent, request.title()));
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok(Map.of("sent", sent, "targetUserId", targetUserId, "title", request.title()));
    }
}
