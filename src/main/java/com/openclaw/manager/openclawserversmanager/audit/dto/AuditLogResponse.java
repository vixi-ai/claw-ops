package com.openclaw.manager.openclawserversmanager.audit.dto;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID userId,
        AuditAction action,
        String entityType,
        UUID entityId,
        String details,
        String ipAddress,
        Instant createdAt
) {
}
