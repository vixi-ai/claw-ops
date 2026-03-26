package com.openclaw.manager.openclawserversmanager.monitoring.engine;

import com.openclaw.manager.openclawserversmanager.monitoring.collector.CollectionResult;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.HealthSnapshot;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.HealthState;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.MetricType;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.MonitoringProfile;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.HealthSnapshotRepository;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.MaintenanceWindowRepository;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.MonitoringProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class HealthEvaluator {

    private static final Logger log = LoggerFactory.getLogger(HealthEvaluator.class);

    private static final BigDecimal DEFAULT_CPU_WARNING = new BigDecimal("80.00");
    private static final BigDecimal DEFAULT_CPU_CRITICAL = new BigDecimal("95.00");
    private static final BigDecimal DEFAULT_MEMORY_WARNING = new BigDecimal("80.00");
    private static final BigDecimal DEFAULT_MEMORY_CRITICAL = new BigDecimal("95.00");
    private static final BigDecimal DEFAULT_DISK_WARNING = new BigDecimal("85.00");
    private static final BigDecimal DEFAULT_DISK_CRITICAL = new BigDecimal("95.00");

    private final HealthSnapshotRepository healthSnapshotRepository;
    private final MonitoringProfileRepository monitoringProfileRepository;
    private final MaintenanceWindowRepository maintenanceWindowRepository;

    public HealthEvaluator(HealthSnapshotRepository healthSnapshotRepository,
                           MonitoringProfileRepository monitoringProfileRepository,
                           MaintenanceWindowRepository maintenanceWindowRepository) {
        this.healthSnapshotRepository = healthSnapshotRepository;
        this.monitoringProfileRepository = monitoringProfileRepository;
        this.maintenanceWindowRepository = maintenanceWindowRepository;
    }

    @Transactional
    public HealthSnapshot evaluate(CollectionResult result) {
        UUID serverId = result.serverId();
        Instant collectedAt = result.collectedAt();

        HealthSnapshot snapshot = getOrCreateSnapshot(serverId);

        if (!result.sshReachable()) {
            return evaluateUnreachable(snapshot, collectedAt);
        }

        return evaluateMetrics(snapshot, result.metrics(), collectedAt);
    }

    @Transactional
    public HealthSnapshot evaluateUnreachable(UUID serverId, Instant collectedAt) {
        HealthSnapshot snapshot = getOrCreateSnapshot(serverId);
        return evaluateUnreachable(snapshot, collectedAt);
    }

    @Transactional(readOnly = true)
    public HealthSnapshot getHealthSnapshot(UUID serverId) {
        return healthSnapshotRepository.findByServerId(serverId).orElse(null);
    }

    private HealthSnapshot getOrCreateSnapshot(UUID serverId) {
        return healthSnapshotRepository.findByServerId(serverId)
                .orElseGet(() -> {
                    HealthSnapshot s = new HealthSnapshot();
                    s.setServerId(serverId);
                    return s;
                });
    }

    private HealthSnapshot evaluateMetrics(HealthSnapshot snapshot, Map<String, Double> metrics, Instant collectedAt) {
        UUID serverId = snapshot.getServerId();

        // Update raw metric values
        Double cpuUsage = metrics.get(MetricType.CPU_USAGE_PERCENT.name());
        Double memUsage = metrics.get(MetricType.MEMORY_USAGE_PERCENT.name());
        Double diskUsage = metrics.get(MetricType.DISK_USAGE_PERCENT.name());
        Double load1m = metrics.get(MetricType.LOAD_1M.name());
        Double uptimeSeconds = metrics.get(MetricType.UPTIME_SECONDS.name());
        Double processCount = metrics.get(MetricType.PROCESS_COUNT.name());

        snapshot.setCpuUsage(cpuUsage);
        snapshot.setMemoryUsage(memUsage);
        snapshot.setDiskUsage(diskUsage);
        snapshot.setLoad1m(load1m);
        snapshot.setUptimeSeconds(uptimeSeconds != null ? uptimeSeconds.longValue() : null);
        snapshot.setProcessCount(processCount != null ? processCount.intValue() : null);

        snapshot.setSshReachable(true);
        snapshot.setLastCheckAt(collectedAt);
        snapshot.setLastSuccessfulCheckAt(collectedAt);
        snapshot.setConsecutiveFailures(0);

        // Resolve thresholds
        MonitoringProfile profile = monitoringProfileRepository.findByServerId(serverId).orElse(null);

        BigDecimal cpuWarn = profile != null ? profile.getCpuWarningThreshold() : DEFAULT_CPU_WARNING;
        BigDecimal cpuCrit = profile != null ? profile.getCpuCriticalThreshold() : DEFAULT_CPU_CRITICAL;
        BigDecimal memWarn = profile != null ? profile.getMemoryWarningThreshold() : DEFAULT_MEMORY_WARNING;
        BigDecimal memCrit = profile != null ? profile.getMemoryCriticalThreshold() : DEFAULT_MEMORY_CRITICAL;
        BigDecimal diskWarn = profile != null ? profile.getDiskWarningThreshold() : DEFAULT_DISK_WARNING;
        BigDecimal diskCrit = profile != null ? profile.getDiskCriticalThreshold() : DEFAULT_DISK_CRITICAL;

        // Evaluate per-resource states
        HealthState cpuState = evaluateResourceState(cpuUsage, cpuWarn, cpuCrit);
        HealthState memState = evaluateResourceState(memUsage, memWarn, memCrit);
        HealthState diskState = evaluateResourceState(diskUsage, diskWarn, diskCrit);

        snapshot.setCpuState(cpuState);
        snapshot.setMemoryState(memState);
        snapshot.setDiskState(diskState);

        // Check maintenance window
        boolean inMaintenance = isInMaintenance(serverId);

        if (inMaintenance) {
            applyStateTransition(snapshot, HealthState.MAINTENANCE, collectedAt);
        } else {
            HealthState computedState = worstState(cpuState, memState, diskState);
            applyStateTransition(snapshot, computedState, collectedAt);
        }

        return healthSnapshotRepository.save(snapshot);
    }

    private HealthSnapshot evaluateUnreachable(HealthSnapshot snapshot, Instant collectedAt) {
        UUID serverId = snapshot.getServerId();

        snapshot.setSshReachable(false);
        snapshot.setLastCheckAt(collectedAt);
        snapshot.setConsecutiveFailures(snapshot.getConsecutiveFailures() + 1);

        boolean inMaintenance = isInMaintenance(serverId);

        if (inMaintenance) {
            applyStateTransition(snapshot, HealthState.MAINTENANCE, collectedAt);
        } else {
            // UNREACHABLE is always immediate — no flapping protection
            applyStateTransition(snapshot, HealthState.UNREACHABLE, collectedAt);
        }

        return healthSnapshotRepository.save(snapshot);
    }

    /**
     * Applies state transition with flapping protection.
     *
     * Transition rules:
     * - UNKNOWN → any: always immediate (initial state)
     * - Any → CRITICAL: immediate (critical is always urgent)
     * - Any → UNREACHABLE: immediate (SSH failure)
     * - Any → MAINTENANCE: immediate (manual override)
     * - HEALTHY → WARNING: requires 2 consecutive WARNING evaluations
     * - WARNING → HEALTHY: requires 2 consecutive HEALTHY evaluations
     * - CRITICAL → WARNING: requires 2 consecutive WARNING evaluations
     * - CRITICAL → HEALTHY: requires 3 consecutive HEALTHY evaluations
     * - UNREACHABLE → previous: requires 1 successful check
     */
    private void applyStateTransition(HealthSnapshot snapshot, HealthState computedState, Instant collectedAt) {
        HealthState currentState = snapshot.getOverallState();

        // Same state — reset flapping counter
        if (computedState == currentState) {
            snapshot.setProposedState(null);
            snapshot.setConsecutiveInProposed(0);
            return;
        }

        int requiredConsecutive = getRequiredConsecutive(currentState, computedState);

        if (requiredConsecutive <= 1) {
            // Immediate transition
            performTransition(snapshot, currentState, computedState, collectedAt);
            return;
        }

        // Flapping protection: track proposed state
        if (computedState == snapshot.getProposedState()) {
            int count = snapshot.getConsecutiveInProposed() + 1;
            snapshot.setConsecutiveInProposed(count);

            if (count >= requiredConsecutive) {
                // Threshold met — transition
                performTransition(snapshot, currentState, computedState, collectedAt);
            }
        } else {
            // New proposed state — start counting
            snapshot.setProposedState(computedState);
            snapshot.setConsecutiveInProposed(1);
        }
    }

    private void performTransition(HealthSnapshot snapshot, HealthState from, HealthState to, Instant collectedAt) {
        snapshot.setPreviousState(from);
        snapshot.setOverallState(to);
        snapshot.setStateChangedAt(collectedAt);
        snapshot.setProposedState(null);
        snapshot.setConsecutiveInProposed(0);

        log.info("Server {} health state changed: {} → {}", snapshot.getServerId(), from, to);
    }

    private int getRequiredConsecutive(HealthState current, HealthState proposed) {
        // UNKNOWN → any: immediate
        if (current == HealthState.UNKNOWN) return 1;

        // Any → CRITICAL: immediate
        if (proposed == HealthState.CRITICAL) return 1;

        // Any → UNREACHABLE: immediate
        if (proposed == HealthState.UNREACHABLE) return 1;

        // Any → MAINTENANCE: immediate
        if (proposed == HealthState.MAINTENANCE) return 1;

        // UNREACHABLE → any: 1 successful check
        if (current == HealthState.UNREACHABLE) return 1;

        // MAINTENANCE → any: immediate (window ended)
        if (current == HealthState.MAINTENANCE) return 1;

        // CRITICAL → HEALTHY: requires 3
        if (current == HealthState.CRITICAL && proposed == HealthState.HEALTHY) return 3;

        // CRITICAL → WARNING: requires 2
        if (current == HealthState.CRITICAL && proposed == HealthState.WARNING) return 2;

        // WARNING → HEALTHY: requires 2
        if (current == HealthState.WARNING && proposed == HealthState.HEALTHY) return 2;

        // HEALTHY → WARNING: requires 2
        if (current == HealthState.HEALTHY && proposed == HealthState.WARNING) return 2;

        // Default: requires 2
        return 2;
    }

    private boolean isInMaintenance(UUID serverId) {
        Instant now = Instant.now();
        return !maintenanceWindowRepository
                .findByServerIdAndStartAtBeforeAndEndAtAfter(serverId, now, now).isEmpty();
    }

    private HealthState evaluateResourceState(Double value, BigDecimal warnThreshold, BigDecimal critThreshold) {
        if (value == null) {
            return HealthState.UNKNOWN;
        }
        if (value >= critThreshold.doubleValue()) {
            return HealthState.CRITICAL;
        }
        if (value >= warnThreshold.doubleValue()) {
            return HealthState.WARNING;
        }
        return HealthState.HEALTHY;
    }

    private HealthState worstState(HealthState... states) {
        HealthState worst = HealthState.HEALTHY;
        for (HealthState state : states) {
            if (state == null) continue;
            if (severity(state) > severity(worst)) {
                worst = state;
            }
        }
        return worst;
    }

    private int severity(HealthState state) {
        return switch (state) {
            case HEALTHY -> 0;
            case UNKNOWN -> 1;
            case WARNING -> 2;
            case CRITICAL -> 3;
            case UNREACHABLE -> 4;
            case MAINTENANCE -> -1;
        };
    }
}
