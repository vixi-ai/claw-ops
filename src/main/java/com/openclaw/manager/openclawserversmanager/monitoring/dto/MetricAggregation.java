package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.MetricType;

public record MetricAggregation(
        MetricType metricType,
        Double min,
        Double max,
        Double avg,
        long sampleCount
) {}
