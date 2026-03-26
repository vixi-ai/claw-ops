package com.openclaw.manager.openclawserversmanager.monitoring.service;

import com.openclaw.manager.openclawserversmanager.monitoring.collector.CollectionResult;
import com.openclaw.manager.openclawserversmanager.monitoring.engine.HealthEvaluator;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.MetricSample;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.MetricType;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.MetricSampleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    private final MetricSampleRepository metricSampleRepository;
    private final HealthEvaluator healthEvaluator;

    public MetricsService(MetricSampleRepository metricSampleRepository,
                          HealthEvaluator healthEvaluator) {
        this.metricSampleRepository = metricSampleRepository;
        this.healthEvaluator = healthEvaluator;
    }

    @Transactional
    public void processCollectionResult(CollectionResult result) {
        // Persist metric samples (only if SSH was reachable and we have data)
        if (result.sshReachable() && !result.metrics().isEmpty()) {
            List<MetricSample> samples = buildMetricSamples(result.serverId(), result.metrics(), result.collectedAt());
            if (!samples.isEmpty()) {
                metricSampleRepository.saveAll(samples);
            }
        }

        // Delegate health evaluation to HealthEvaluator (handles all state logic)
        healthEvaluator.evaluate(result);
    }

    @Transactional(readOnly = true)
    public List<MetricSample> queryMetrics(UUID serverId, MetricType metricType, Instant from, Instant to) {
        return metricSampleRepository.findByServerIdAndMetricTypeAndCollectedAtBetweenOrderByCollectedAtDesc(
                serverId, metricType, from, to);
    }

    @Transactional(readOnly = true)
    public List<MetricSample> getLatestMetrics(UUID serverId) {
        return metricSampleRepository.findLatestByServerId(serverId);
    }

    @Transactional
    public long deleteOldMetrics(Instant before) {
        long deleted = metricSampleRepository.deleteByCollectedAtBefore(before);
        log.info("Deleted {} old metric samples older than {}", deleted, before);
        return deleted;
    }

    private List<MetricSample> buildMetricSamples(UUID serverId, Map<String, Double> metrics, Instant collectedAt) {
        List<MetricSample> samples = new ArrayList<>();

        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            String key = entry.getKey();
            Double value = entry.getValue();
            if (value == null) continue;

            MetricType metricType;
            String label = null;

            // Keys with labels use "TYPE:label" format
            int colonIdx = key.indexOf(':');
            if (colonIdx > 0) {
                metricType = parseMetricType(key.substring(0, colonIdx));
                label = key.substring(colonIdx + 1);
            } else {
                metricType = parseMetricType(key);
            }

            if (metricType == null) continue;

            MetricSample sample = new MetricSample();
            sample.setServerId(serverId);
            sample.setMetricType(metricType);
            sample.setMetricLabel(label);
            sample.setValue(value);
            sample.setCollectedAt(collectedAt);
            samples.add(sample);
        }

        return samples;
    }

    private MetricType parseMetricType(String name) {
        try {
            return MetricType.valueOf(name);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown metric type: {}", name);
            return null;
        }
    }
}
