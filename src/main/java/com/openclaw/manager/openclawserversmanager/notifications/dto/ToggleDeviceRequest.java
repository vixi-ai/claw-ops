package com.openclaw.manager.openclawserversmanager.notifications.dto;

public record ToggleDeviceRequest(
        boolean enabled,
        String fcmToken,
        String pushEndpoint,
        String pushKeyAuth,
        String pushKeyP256dh
) {
}
