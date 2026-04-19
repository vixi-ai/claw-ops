package com.openclaw.manager.openclawserversmanager.domains.dto;

import com.openclaw.manager.openclawserversmanager.domains.entity.DomainJobStatus;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainJobStep;

import java.time.Instant;
import java.util.UUID;

public record DomainJobResponse(
        UUID id,
        UUID domainAssignmentId,
        UUID serverId,
        String hostname,
        DomainJobStep currentStep,
        DomainJobStatus status,
        int retryCount,
        int maxRetries,
        String logs,
        String errorMessage,
        UUID triggeredBy,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
}
