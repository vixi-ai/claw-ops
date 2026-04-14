package com.openclaw.manager.openclawserversmanager.monitoring.dto;

public record UptimeResponse(
        double uptimePercent,
        long totalSeconds,
        long downSeconds,
        long totalChecks,
        long failedChecks
) {}
