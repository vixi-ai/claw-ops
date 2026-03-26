package com.openclaw.manager.openclawserversmanager.monitoring.scheduler;

import com.openclaw.manager.openclawserversmanager.monitoring.collector.CollectionResult;
import com.openclaw.manager.openclawserversmanager.monitoring.collector.MetricCollector;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.HealthSnapshot;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.HealthState;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.MonitoringProfile;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.HealthSnapshotRepository;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.MonitoringProfileRepository;
import com.openclaw.manager.openclawserversmanager.monitoring.service.MetricsService;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.repository.ServerRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class MonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitoringScheduler.class);

    private final Set<UUID> inFlightChecks = ConcurrentHashMap.newKeySet();

    @Value("${monitoring.check.default-interval:60}")
    private int defaultCheckIntervalSeconds;

    @Value("${monitoring.metrics.retention-days:7}")
    private int retentionDays;

    @Value("${monitoring.check.stale-multiplier:3}")
    private int staleMultiplier;

    private final ServerRepository serverRepository;
    private final MonitoringProfileRepository monitoringProfileRepository;
    private final HealthSnapshotRepository healthSnapshotRepository;
    private final MetricCollector metricCollector;
    private final MetricsService metricsService;
    private final Executor monitoringExecutor;

    public MonitoringScheduler(ServerRepository serverRepository,
                               MonitoringProfileRepository monitoringProfileRepository,
                               HealthSnapshotRepository healthSnapshotRepository,
                               MetricCollector metricCollector,
                               MetricsService metricsService,
                               @Qualifier("monitoringExecutor") Executor monitoringExecutor) {
        this.serverRepository = serverRepository;
        this.monitoringProfileRepository = monitoringProfileRepository;
        this.healthSnapshotRepository = healthSnapshotRepository;
        this.metricCollector = metricCollector;
        this.metricsService = metricsService;
        this.monitoringExecutor = monitoringExecutor;
    }

    @PostConstruct
    public void onStartup() {
        log.info("Monitoring scheduler starting — scheduling immediate check for all enabled servers");
        monitoringExecutor.execute(this::schedulerTick);
    }

    @Scheduled(fixedDelayString = "${monitoring.scheduler.interval:30000}")
    public void schedulerTick() {
        long tickStart = System.currentTimeMillis();
        int submitted = 0;
        int skipped = 0;

        try {
            List<Server> servers = getServersDueForCheck();

            for (Server server : servers) {
                if (inFlightChecks.contains(server.getId())) {
                    skipped++;
                    continue;
                }

                inFlightChecks.add(server.getId());
                submitted++;

                monitoringExecutor.execute(() -> runCheck(server));
            }
        } catch (Exception e) {
            log.error("Scheduler tick failed: {}", e.getMessage(), e);
        }

        long tickDuration = System.currentTimeMillis() - tickStart;
        if (submitted > 0 || skipped > 0) {
            log.info("Scheduler tick completed in {}ms — submitted={}, skipped={} (in-flight), total-in-flight={}",
                    tickDuration, submitted, skipped, inFlightChecks.size());
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void retentionCleanup() {
        log.info("Starting metric retention cleanup (retaining {} days)", retentionDays);
        try {
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            long deleted = metricsService.deleteOldMetrics(cutoff);
            log.info("Retention cleanup completed — deleted {} metric samples", deleted);
        } catch (Exception e) {
            log.error("Retention cleanup failed: {}", e.getMessage(), e);
        }
    }

    private void runCheck(Server server) {
        long start = System.currentTimeMillis();
        try {
            CollectionResult result = metricCollector.collect(server);
            metricsService.processCollectionResult(result);

            long duration = System.currentTimeMillis() - start;
            if (!result.sshReachable()) {
                log.warn("Check completed for server {} (UNREACHABLE) in {}ms — errors: {}",
                        server.getName(), duration, result.errors());
            } else if (!result.errors().isEmpty()) {
                log.warn("Check completed for server {} in {}ms with partial errors: {}",
                        server.getName(), duration, result.errors());
            } else {
                log.debug("Check completed for server {} in {}ms — {} metrics collected",
                        server.getName(), duration, result.metrics().size());
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Check failed for server {} after {}ms: {}", server.getName(), duration, e.getMessage());
        } finally {
            inFlightChecks.remove(server.getId());
        }
    }

    private List<Server> getServersDueForCheck() {
        List<Server> allServers = serverRepository.findAll();
        Instant now = Instant.now();

        List<Server> dueServers = allServers.stream()
                .filter(server -> isCheckDue(server, now))
                .sorted(priorityComparator())
                .toList();

        // Mark stale servers as UNKNOWN
        for (Server server : allServers) {
            detectStale(server, now);
        }

        return dueServers;
    }

    private boolean isCheckDue(Server server, Instant now) {
        MonitoringProfile profile = getOrCreateProfile(server.getId());

        if (!profile.isEnabled()) {
            return false;
        }

        Optional<HealthSnapshot> snapshotOpt = healthSnapshotRepository.findByServerId(server.getId());
        if (snapshotOpt.isEmpty()) {
            return true; // Never checked — due immediately
        }

        HealthSnapshot snapshot = snapshotOpt.get();
        if (snapshot.getLastCheckAt() == null) {
            return true;
        }

        int intervalSeconds = profile.getCheckIntervalSeconds();
        Instant nextDue = snapshot.getLastCheckAt().plusSeconds(intervalSeconds);
        return now.isAfter(nextDue) || now.equals(nextDue);
    }

    private void detectStale(Server server, Instant now) {
        MonitoringProfile profile = monitoringProfileRepository.findByServerId(server.getId()).orElse(null);
        if (profile == null || !profile.isEnabled()) return;

        Optional<HealthSnapshot> snapshotOpt = healthSnapshotRepository.findByServerId(server.getId());
        if (snapshotOpt.isEmpty()) return;

        HealthSnapshot snapshot = snapshotOpt.get();
        if (snapshot.getLastCheckAt() == null) return;
        if (snapshot.getOverallState() == HealthState.MAINTENANCE) return;

        int intervalSeconds = profile.getCheckIntervalSeconds();
        Instant staleThreshold = snapshot.getLastCheckAt().plusSeconds((long) intervalSeconds * staleMultiplier);

        if (now.isAfter(staleThreshold) && snapshot.getOverallState() != HealthState.UNKNOWN) {
            log.warn("Server {} is stale — last check at {}, marking UNKNOWN", server.getName(), snapshot.getLastCheckAt());
            snapshot.setOverallState(HealthState.UNKNOWN);
            healthSnapshotRepository.save(snapshot);
        }
    }

    private MonitoringProfile getOrCreateProfile(UUID serverId) {
        return monitoringProfileRepository.findByServerId(serverId)
                .orElseGet(() -> {
                    MonitoringProfile profile = new MonitoringProfile();
                    profile.setServerId(serverId);
                    profile.setCheckIntervalSeconds(defaultCheckIntervalSeconds);
                    log.info("Auto-created monitoring profile for server {} with default settings", serverId);
                    return monitoringProfileRepository.save(profile);
                });
    }

    private Comparator<Server> priorityComparator() {
        return (a, b) -> {
            int priorityA = getCheckPriority(a.getId());
            int priorityB = getCheckPriority(b.getId());
            return Integer.compare(priorityA, priorityB);
        };
    }

    private int getCheckPriority(UUID serverId) {
        return healthSnapshotRepository.findByServerId(serverId)
                .map(snapshot -> switch (snapshot.getOverallState()) {
                    case CRITICAL -> 0;
                    case UNREACHABLE -> 1;
                    case WARNING -> 2;
                    case UNKNOWN -> 3;
                    case HEALTHY -> 4;
                    case MAINTENANCE -> 5;
                })
                .orElse(3); // No snapshot = UNKNOWN priority
    }
}
