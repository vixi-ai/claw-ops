package com.openclaw.manager.openclawserversmanager.domains.dto;

import com.openclaw.manager.openclawserversmanager.domains.entity.SslStatus;

import java.time.Instant;
import java.util.UUID;

public record SslCertificateResponse(
        UUID id,
        UUID serverId,
        UUID assignmentId,
        UUID provisioningJobId,
        String hostname,
        SslStatus status,
        String adminEmail,
        int targetPort,
        Instant expiresAt,
        Instant lastRenewedAt,
        String lastError,
        boolean hostNginxManaged,
        Instant createdAt,
        Instant updatedAt
) {
}
