package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import java.time.Instant;

public record MetricPoint(
    Instant timestamp,
    double value
) {}
