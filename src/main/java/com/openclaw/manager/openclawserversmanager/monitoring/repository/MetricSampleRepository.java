package com.openclaw.manager.openclawserversmanager.monitoring.repository;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.MetricSample;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.MetricType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface MetricSampleRepository extends JpaRepository<MetricSample, UUID> {

    List<MetricSample> findByServerIdAndMetricTypeAndCollectedAtBetweenOrderByCollectedAtDesc(
            UUID serverId, MetricType metricType, Instant from, Instant to);

    List<MetricSample> findByServerIdAndCollectedAtBetweenOrderByCollectedAtDesc(
            UUID serverId, Instant from, Instant to);

    @Query("SELECT m FROM MetricSample m WHERE m.serverId = :serverId AND m.collectedAt = " +
           "(SELECT MAX(m2.collectedAt) FROM MetricSample m2 WHERE m2.serverId = :serverId AND m2.metricType = m.metricType)")
    List<MetricSample> findLatestByServerId(UUID serverId);

    @Query("SELECT AVG(m.value) FROM MetricSample m WHERE m.serverId = :serverId AND m.metricType = :metricType AND m.collectedAt BETWEEN :from AND :to")
    Double findAvgValue(UUID serverId, MetricType metricType, Instant from, Instant to);

    @Query("SELECT MIN(m.value) FROM MetricSample m WHERE m.serverId = :serverId AND m.metricType = :metricType AND m.collectedAt BETWEEN :from AND :to")
    Double findMinValue(UUID serverId, MetricType metricType, Instant from, Instant to);

    @Query("SELECT MAX(m.value) FROM MetricSample m WHERE m.serverId = :serverId AND m.metricType = :metricType AND m.collectedAt BETWEEN :from AND :to")
    Double findMaxValue(UUID serverId, MetricType metricType, Instant from, Instant to);

    @Query("SELECT COUNT(m) FROM MetricSample m WHERE m.serverId = :serverId AND m.metricType = :metricType AND m.collectedAt BETWEEN :from AND :to")
    long countSamples(UUID serverId, MetricType metricType, Instant from, Instant to);

    @Modifying
    @Query("DELETE FROM MetricSample m WHERE m.collectedAt < :before")
    long deleteByCollectedAtBefore(Instant before);
}
