package com.openclaw.manager.openclawserversmanager.audit.dto;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;

import java.time.Instant;
import java.util.UUID;

public record AuditLogFilter(
        UUID userId,
        AuditAction action,
        String entityType,
        UUID entityId,
        Instant from,
        Instant to
) {
}
