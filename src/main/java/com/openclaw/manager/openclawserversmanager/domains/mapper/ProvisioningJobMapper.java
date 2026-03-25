package com.openclaw.manager.openclawserversmanager.domains.mapper;

import com.openclaw.manager.openclawserversmanager.domains.dto.ProvisioningJobResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProvisioningJob;

public final class ProvisioningJobMapper {

    private ProvisioningJobMapper() {
    }

    public static ProvisioningJobResponse toResponse(ProvisioningJob job, String hostname) {
        return new ProvisioningJobResponse(
                job.getId(),
                job.getDomainAssignmentId(),
                job.getServerId(),
                hostname,
                job.getCurrentStep(),
                job.getStatus(),
                job.getRetryCount(),
                job.getMaxRetries(),
                job.getLogs(),
                job.getErrorMessage(),
                job.getTriggeredBy(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt()
        );
    }
}
