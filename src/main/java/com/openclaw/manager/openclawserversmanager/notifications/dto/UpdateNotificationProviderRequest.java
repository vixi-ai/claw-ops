package com.openclaw.manager.openclawserversmanager.notifications.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record UpdateNotificationProviderRequest(
        @Size(max = 100) String displayName,
        Boolean enabled,
        UUID credentialId,
        Map<String, Object> providerSettings
) {
}
