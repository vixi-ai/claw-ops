package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.IncidentEvent;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.IncidentEventType;

import java.time.Instant;
import java.util.UUID;

public record IncidentEventResponse(
        UUID id,
        UUID incidentId,
        UUID authorId,
        IncidentEventType eventType,
        String content,
        Instant createdAt
) {
    public static IncidentEventResponse from(IncidentEvent e) {
        return new IncidentEventResponse(
                e.getId(), e.getIncidentId(), e.getAuthorId(),
                e.getEventType(), e.getContent(), e.getCreatedAt()
        );
    }
}
