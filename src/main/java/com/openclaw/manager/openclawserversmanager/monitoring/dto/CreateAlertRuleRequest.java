package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.*;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record CreateAlertRuleRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        UUID serverId,
        @NotNull AlertRuleType ruleType,
        @NotNull MetricType metricType,
        @NotNull ConditionOperator conditionOperator,
        double thresholdValue,
        @NotNull IncidentSeverity severity,
        @Min(1) @Max(100) int consecutiveFailures,
        @Min(1) @Max(1440) int cooldownMinutes,
        List<UUID> channelIds
) {
    public CreateAlertRuleRequest {
        if (consecutiveFailures == 0) consecutiveFailures = 3;
        if (cooldownMinutes == 0) cooldownMinutes = 15;
    }
}
