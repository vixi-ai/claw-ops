package com.openclaw.manager.openclawserversmanager.domains.dto;

import com.openclaw.manager.openclawserversmanager.domains.entity.DnsProviderType;
import com.openclaw.manager.openclawserversmanager.domains.entity.HealthStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProviderAccountResponse(
        UUID id,
        DnsProviderType providerType,
        String displayName,
        boolean enabled,
        UUID credentialId,
        Map<String, Object> providerSettings,
        HealthStatus healthStatus,
        Instant createdAt,
        Instant updatedAt
) {
}
