package com.openclaw.manager.openclawserversmanager.domains.mapper;

import com.openclaw.manager.openclawserversmanager.domains.dto.DomainJobResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainAssignmentJob;

public final class DomainAssignmentJobMapper {

    private DomainAssignmentJobMapper() {
    }

    public static DomainJobResponse toResponse(DomainAssignmentJob job, String hostname) {
        return new DomainJobResponse(
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
