package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record UpdateMonitoringProfileRequest(
    Boolean enabled,
    @Min(10) @Max(3600) Integer checkIntervalSeconds,
    @Min(1) @Max(365) Integer metricRetentionDays,
    @Min(0) @Max(100) BigDecimal cpuWarningThreshold,
    @Min(0) @Max(100) BigDecimal cpuCriticalThreshold,
    @Min(0) @Max(100) BigDecimal memoryWarningThreshold,
    @Min(0) @Max(100) BigDecimal memoryCriticalThreshold,
    @Min(0) @Max(100) BigDecimal diskWarningThreshold,
    @Min(0) @Max(100) BigDecimal diskCriticalThreshold
) {}
