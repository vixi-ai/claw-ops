package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AlertRuleResponse(
        UUID id,
        String name,
        String description,
        UUID serverId,
        AlertRuleType ruleType,
        MetricType metricType,
        ConditionOperator conditionOperator,
        double thresholdValue,
        IncidentSeverity severity,
        int consecutiveFailures,
        int cooldownMinutes,
        boolean enabled,
        List<ChannelSummary> channels,
        Instant createdAt,
        Instant updatedAt
) {
    public record ChannelSummary(UUID id, String name, NotificationChannelType type) {}

    public static AlertRuleResponse from(AlertRule rule) {
        List<ChannelSummary> channels = rule.getNotificationChannels() != null
                ? rule.getNotificationChannels().stream()
                    .map(c -> new ChannelSummary(c.getId(), c.getName(), c.getChannelType()))
                    .toList()
                : List.of();

        return new AlertRuleResponse(
                rule.getId(), rule.getName(), rule.getDescription(),
                rule.getServerId(), rule.getRuleType(), rule.getMetricType(),
                rule.getConditionOperator(), rule.getThresholdValue(),
                rule.getSeverity(), rule.getConsecutiveFailures(),
                rule.getCooldownMinutes(), rule.isEnabled(),
                channels, rule.getCreatedAt(), rule.getUpdatedAt()
        );
    }
}
