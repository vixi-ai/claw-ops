package com.openclaw.manager.openclawserversmanager.domains.dto;

import com.openclaw.manager.openclawserversmanager.domains.entity.DomainEventAction;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainEventOutcome;

import java.time.Instant;
import java.util.UUID;

public record DomainEventResponse(
        UUID id,
        UUID assignmentId,
        UUID zoneId,
        DomainEventAction action,
        DomainEventOutcome outcome,
        String providerCorrelationId,
        String details,
        Instant createdAt
) {
}
