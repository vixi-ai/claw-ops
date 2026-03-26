package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MonitoringProfileResponse(
    UUID id,
    UUID serverId,
    boolean enabled,
    int checkIntervalSeconds,
    int metricRetentionDays,
    BigDecimal cpuWarningThreshold,
    BigDecimal cpuCriticalThreshold,
    BigDecimal memoryWarningThreshold,
    BigDecimal memoryCriticalThreshold,
    BigDecimal diskWarningThreshold,
    BigDecimal diskCriticalThreshold,
    Instant createdAt,
    Instant updatedAt
) {}
