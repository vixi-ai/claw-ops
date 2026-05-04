package com.openclaw.manager.openclawserversmanager.containerlogs.scheduler;

import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import com.openclaw.manager.openclawserversmanager.containerlogs.metrics.ContainerLogMetrics;
import com.openclaw.manager.openclawserversmanager.containerlogs.repository.ContainerLogRepository;
import com.openclaw.manager.openclawserversmanager.containerlogs.service.RetentionSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;

@Component
public class ContainerLogRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContainerLogRetentionScheduler.class);

    private final ContainerLogRepository repository;
    private final RetentionSettingsService retentionService;
    private final ContainerLogMetrics metrics;

    public ContainerLogRetentionScheduler(ContainerLogRepository repository,
                                          RetentionSettingsService retentionService,
                                          ContainerLogMetrics metrics) {
        this.repository = repository;
        this.retentionService = retentionService;
        this.metrics = metrics;
    }

    @Scheduled(cron = "${container-logs.retention.cron:0 30 3 * * *}")
    @Transactional
    public Map<ContainerService, Long> purge() {
        Map<ContainerService, Long> deletedByService = new EnumMap<>(ContainerService.class);
        long total = 0;
        for (ContainerService svc : ContainerService.values()) {
            int days = retentionService.getDaysFor(svc);
            Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
            long deleted = repository.deleteByServiceAndLogTsBefore(svc, cutoff);
            deletedByService.put(svc, deleted);
            metrics.recordRetentionDelete(svc, deleted);
            total += deleted;
            log.info("Container log retention: service={} retentionDays={} deleted={}", svc, days, deleted);
        }
        log.info("Container log retention pass complete — totalDeleted={}", total);
        return deletedByService;
    }
}
