package com.openclaw.manager.openclawserversmanager.domains.dto;

import com.openclaw.manager.openclawserversmanager.domains.entity.SslStatus;

import java.time.Instant;
import java.util.UUID;

public record SslCertificateResponse(
        UUID id,
        UUID serverId,
        UUID assignmentId,
        String hostname,
        SslStatus status,
        String adminEmail,
        int targetPort,
        Instant expiresAt,
        Instant lastRenewedAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
}
