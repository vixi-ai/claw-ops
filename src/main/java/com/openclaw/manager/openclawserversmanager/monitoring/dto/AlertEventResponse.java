package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.*;

import java.time.Instant;
import java.util.UUID;

public record AlertEventResponse(
        UUID id,
        UUID alertRuleId,
        String ruleName,
        UUID serverId,
        UUID incidentId,
        IncidentSeverity severity,
        AlertStatus status,
        MetricType metricType,
        Double metricValue,
        String message,
        UUID acknowledgedBy,
        Instant acknowledgedAt,
        Instant resolvedAt,
        Instant firedAt
) {
    public static AlertEventResponse from(AlertEvent event, String ruleName) {
        return new AlertEventResponse(
                event.getId(), event.getAlertRuleId(), ruleName,
                event.getServerId(), event.getIncidentId(),
                event.getSeverity(), event.getStatus(),
                event.getMetricType(), event.getMetricValue(),
                event.getMessage(), event.getAcknowledgedBy(),
                event.getAcknowledgedAt(), event.getResolvedAt(),
                event.getFiredAt()
        );
    }
}
