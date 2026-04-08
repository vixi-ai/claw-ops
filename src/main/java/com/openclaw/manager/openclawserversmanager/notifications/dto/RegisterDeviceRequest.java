package com.openclaw.manager.openclawserversmanager.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
        @NotBlank @Size(max = 100) String deviceName,
        @NotBlank @Size(max = 30) String platform,
        String fcmToken,
        String pushEndpoint,
        String pushKeyAuth,
        String pushKeyP256dh
) {}
