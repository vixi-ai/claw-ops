package com.openclaw.manager.openclawserversmanager.domains.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ManagedZoneResponse(
        UUID id,
        String zoneName,
        UUID providerAccountId,
        boolean active,
        boolean defaultForAutoAssign,
        int defaultTtl,
        String providerZoneId,
        String environmentTag,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
}
