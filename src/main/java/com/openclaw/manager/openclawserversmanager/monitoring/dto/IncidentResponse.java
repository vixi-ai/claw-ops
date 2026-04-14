package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.Incident;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.IncidentSeverity;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        String title,
        String description,
        UUID serverId,
        IncidentSeverity severity,
        IncidentStatus status,
        Instant openedAt,
        Instant acknowledgedAt,
        Instant resolvedAt,
        Instant closedAt,
        UUID resolvedBy,
        String rootCause
) {
    public static IncidentResponse from(Incident i) {
        return new IncidentResponse(
                i.getId(), i.getTitle(), i.getDescription(),
                i.getServerId(), i.getSeverity(), i.getStatus(),
                i.getOpenedAt(), i.getAcknowledgedAt(),
                i.getResolvedAt(), i.getClosedAt(),
                i.getResolvedBy(), i.getRootCause()
        );
    }
}
