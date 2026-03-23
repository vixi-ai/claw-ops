package com.openclaw.manager.openclawserversmanager.domains.mapper;

import com.openclaw.manager.openclawserversmanager.domains.dto.DomainAssignmentResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainAssignment;

public final class DomainAssignmentMapper {

    private DomainAssignmentMapper() {
    }

    public static DomainAssignmentResponse toResponse(DomainAssignment assignment, String zoneName) {
        return new DomainAssignmentResponse(
                assignment.getId(),
                assignment.getZoneId(),
                zoneName,
                assignment.getHostname(),
                assignment.getRecordType(),
                assignment.getTargetValue(),
                assignment.getAssignmentType(),
                assignment.getResourceId(),
                assignment.getStatus(),
                assignment.getProviderRecordId(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt()
        );
    }
}
