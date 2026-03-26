package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.MetricType;

import java.util.List;
import java.util.UUID;

public record MetricHistoryResponse(
    UUID serverId,
    MetricType metricType,
    String label,
    List<MetricPoint> dataPoints
) {}
