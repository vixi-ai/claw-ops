package com.openclaw.manager.openclawserversmanager.notifications.dto;

import jakarta.validation.constraints.NotBlank;

public record PushSubscribeRequest(
        @NotBlank String endpoint,
        @NotBlank String keyAuth,
        @NotBlank String keyP256dh
) {
}
