package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.HealthState;

import java.time.Instant;
import java.util.UUID;

public record ServerHealthSummary(
    UUID serverId,
    String serverName,
    String hostname,
    String environment,
    HealthState overallState,
    HealthState cpuState,
    HealthState memoryState,
    HealthState diskState,
    Double cpuUsage,
    Double memoryUsage,
    Double diskUsage,
    Double load1m,
    Long uptimeSeconds,
    Integer processCount,
    boolean sshReachable,
    Instant lastCheckAt,
    Instant stateChangedAt
) {}
