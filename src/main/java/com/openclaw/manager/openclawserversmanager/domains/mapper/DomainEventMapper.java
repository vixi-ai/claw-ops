package com.openclaw.manager.openclawserversmanager.domains.mapper;

import com.openclaw.manager.openclawserversmanager.domains.dto.DomainEventResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainEvent;

public final class DomainEventMapper {

    private DomainEventMapper() {
    }

    public static DomainEventResponse toResponse(DomainEvent event) {
        return new DomainEventResponse(
                event.getId(),
                event.getAssignmentId(),
                event.getZoneId(),
                event.getAction(),
                event.getOutcome(),
                event.getProviderCorrelationId(),
                event.getDetails(),
                event.getCreatedAt()
        );
    }
}
