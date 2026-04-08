package com.openclaw.manager.openclawserversmanager.notifications.dto;

import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProviderType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationProviderResponse(
        UUID id,
        NotificationProviderType providerType,
        String displayName,
        boolean enabled,
        boolean isDefault,
        UUID credentialId,
        Map<String, Object> providerSettings,
        Instant createdAt,
        Instant updatedAt
) {
}
