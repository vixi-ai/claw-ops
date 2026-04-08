package com.openclaw.manager.openclawserversmanager.notifications.dto;

import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record CreateNotificationProviderRequest(
        @NotNull NotificationProviderType providerType,
        @NotBlank @Size(max = 100) String displayName,
        UUID credentialId,
        Map<String, Object> providerSettings
) {
}
