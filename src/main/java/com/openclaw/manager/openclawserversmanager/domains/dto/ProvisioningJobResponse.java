package com.openclaw.manager.openclawserversmanager.domains.dto;

import com.openclaw.manager.openclawserversmanager.domains.entity.ProvisioningJobStatus;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProvisioningStep;

import java.time.Instant;
import java.util.UUID;

public record ProvisioningJobResponse(
        UUID id,
        UUID domainAssignmentId,
        UUID serverId,
        String hostname,
        ProvisioningStep currentStep,
        ProvisioningJobStatus status,
        int retryCount,
        int maxRetries,
        String logs,
        String errorMessage,
        String acmeTxtRecordId,
        UUID triggeredBy,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
}
