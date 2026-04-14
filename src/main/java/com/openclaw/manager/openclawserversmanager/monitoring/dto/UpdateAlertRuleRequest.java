package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.*;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record UpdateAlertRuleRequest(
        @Size(max = 100) String name,
        @Size(max = 500) String description,
        Boolean enabled,
        MetricType metricType,
        ConditionOperator conditionOperator,
        Double thresholdValue,
        IncidentSeverity severity,
        @Min(1) @Max(100) Integer consecutiveFailures,
        @Min(1) @Max(1440) Integer cooldownMinutes,
        List<UUID> channelIds
) {}
