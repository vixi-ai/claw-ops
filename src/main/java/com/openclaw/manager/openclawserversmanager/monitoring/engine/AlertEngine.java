package com.openclaw.manager.openclawserversmanager.monitoring.engine;

import com.openclaw.manager.openclawserversmanager.monitoring.collector.CollectionResult;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.*;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.AlertEventRepository;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.AlertRuleRepository;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.MaintenanceWindowRepository;
import com.openclaw.manager.openclawserversmanager.monitoring.service.IncidentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Evaluates alert rules against collected metrics and manages alert lifecycle.
 * Called after each health evaluation in MetricsService.processCollectionResult().
 */
@Component
public class AlertEngine {

    private static final Logger log = LoggerFactory.getLogger(AlertEngine.class);

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final MaintenanceWindowRepository maintenanceWindowRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final IncidentService incidentService;

    public AlertEngine(AlertRuleRepository alertRuleRepository,
                       AlertEventRepository alertEventRepository,
                       MaintenanceWindowRepository maintenanceWindowRepository,
                       ApplicationEventPublisher eventPublisher,
                       @Lazy IncidentService incidentService) {
        this.alertRuleRepository = alertRuleRepository;
        this.alertEventRepository = alertEventRepository;
        this.maintenanceWindowRepository = maintenanceWindowRepository;
        this.eventPublisher = eventPublisher;
        this.incidentService = incidentService;
    }

    @Transactional
    public void evaluate(UUID serverId, CollectionResult result, HealthSnapshot snapshot) {
        // Skip if server is in a maintenance window
        Instant now = Instant.now();
        boolean inMaintenance = !maintenanceWindowRepository
                .findByServerIdAndStartAtBeforeAndEndAtAfter(serverId, now, now)
                .isEmpty();
        if (inMaintenance) {
            log.debug("Server {} in maintenance — skipping alert evaluation", serverId);
            return;
        }

        // Load rules for this server + global rules (server_id IS NULL)
        List<AlertRule> rules = alertRuleRepository.findByServerIdOrServerIdIsNull(serverId);

        for (AlertRule rule : rules) {
            if (!rule.isEnabled()) continue;

            try {
                evaluateRule(rule, serverId, result, snapshot, now);
            } catch (Exception e) {
                log.error("Error evaluating alert rule {} for server {}: {}", rule.getName(), serverId, e.getMessage());
            }
        }
    }

    private void evaluateRule(AlertRule rule, UUID serverId, CollectionResult result,
                              HealthSnapshot snapshot, Instant now) {
        boolean shouldFire = switch (rule.getRuleType()) {
            case THRESHOLD -> evaluateThreshold(rule, result);
            case CONSECUTIVE_FAILURE -> evaluateConsecutiveFailure(rule, snapshot);
            case DEADMAN -> evaluateDeadman(rule, snapshot, now);
        };

        List<AlertEvent> activeAlerts = alertEventRepository
                .findByAlertRuleIdAndStatus(rule.getId(), AlertStatus.ACTIVE);

        if (shouldFire) {
            // Check cooldown — don't fire if an active alert exists within cooldown period
            boolean inCooldown = activeAlerts.stream().anyMatch(a ->
                    a.getFiredAt().plusSeconds(rule.getCooldownMinutes() * 60L).isAfter(now));
            if (inCooldown) {
                log.debug("Rule {} for server {} in cooldown — skipping", rule.getName(), serverId);
                return;
            }

            // Fire new alert
            Double metricValue = extractMetricValue(rule.getMetricType(), result);
            AlertEvent event = new AlertEvent();
            event.setAlertRuleId(rule.getId());
            event.setServerId(serverId);
            event.setSeverity(rule.getSeverity());
            event.setStatus(AlertStatus.ACTIVE);
            event.setMetricType(rule.getMetricType());
            event.setMetricValue(metricValue);
            event.setMessage(buildAlertMessage(rule, serverId, metricValue));
            event.setFiredAt(now);
            alertEventRepository.save(event);

            log.info("Alert fired: rule={}, server={}, severity={}, value={}",
                    rule.getName(), serverId, rule.getSeverity(), metricValue);

            // Auto-create incident for HIGH/CRITICAL alerts
            if (rule.getSeverity() == IncidentSeverity.HIGH || rule.getSeverity() == IncidentSeverity.CRITICAL) {
                try {
                    incidentService.createFromAlert(event);
                } catch (Exception e) {
                    log.error("Failed to auto-create incident for alert {}: {}", event.getId(), e.getMessage());
                }
            }

            // Publish event for notification dispatch
            eventPublisher.publishEvent(new AlertFiredEvent(this, event, rule));

        } else {
            // Auto-resolve active alerts for this rule+server
            for (AlertEvent activeAlert : activeAlerts) {
                if (activeAlert.getServerId().equals(serverId)) {
                    activeAlert.setStatus(AlertStatus.RESOLVED);
                    activeAlert.setResolvedAt(now);
                    alertEventRepository.save(activeAlert);

                    log.info("Alert auto-resolved: rule={}, server={}", rule.getName(), serverId);

                    eventPublisher.publishEvent(new AlertResolvedEvent(this, activeAlert, rule));
                }
            }
        }
    }

    private boolean evaluateThreshold(AlertRule rule, CollectionResult result) {
        if (!result.sshReachable()) return false;

        Double value = extractMetricValue(rule.getMetricType(), result);
        if (value == null) return false;

        return switch (rule.getConditionOperator()) {
            case GREATER_THAN -> value > rule.getThresholdValue();
            case LESS_THAN -> value < rule.getThresholdValue();
            case GREATER_THAN_OR_EQUAL -> value >= rule.getThresholdValue();
            case LESS_THAN_OR_EQUAL -> value <= rule.getThresholdValue();
            case EQUAL -> Double.compare(value, rule.getThresholdValue()) == 0;
            case NOT_EQUAL -> Double.compare(value, rule.getThresholdValue()) != 0;
        };
    }

    private boolean evaluateConsecutiveFailure(AlertRule rule, HealthSnapshot snapshot) {
        if (snapshot == null) return false;
        return snapshot.getConsecutiveFailures() >= rule.getConsecutiveFailures();
    }

    private boolean evaluateDeadman(AlertRule rule, HealthSnapshot snapshot, Instant now) {
        if (snapshot == null) return true; // No snapshot = no heartbeat
        Instant lastCheck = snapshot.getLastSuccessfulCheckAt();
        if (lastCheck == null) return true;
        // Deadman: fire if last successful check exceeds threshold (in seconds)
        long elapsedSeconds = now.getEpochSecond() - lastCheck.getEpochSecond();
        return elapsedSeconds > rule.getThresholdValue();
    }

    private Double extractMetricValue(MetricType metricType, CollectionResult result) {
        if (metricType == null || result.metrics() == null) return null;
        Map<String, Double> metrics = result.metrics();

        // Try direct key match first
        Double value = metrics.get(metricType.name());
        if (value != null) return value;

        // For labeled metrics (DISK_USAGE_PERCENT:/, NETWORK_RX_BYTES:eth0), find max
        String prefix = metricType.name() + ":";
        return metrics.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .max(Double::compareTo)
                .orElse(null);
    }

    private String buildAlertMessage(AlertRule rule, UUID serverId, Double metricValue) {
        String valueStr = metricValue != null ? String.format("%.2f", metricValue) : "N/A";
        return String.format("[%s] %s: %s %s %s (current: %s) on server %s",
                rule.getSeverity(), rule.getName(),
                rule.getMetricType(), rule.getConditionOperator(),
                rule.getThresholdValue(), valueStr, serverId);
    }

    // ── Spring Application Events for notification dispatch ──

    public record AlertFiredEvent(Object source, AlertEvent alertEvent, AlertRule alertRule) {
        public AlertFiredEvent {
            // compact constructor
        }
    }

    public record AlertResolvedEvent(Object source, AlertEvent alertEvent, AlertRule alertRule) {
        public AlertResolvedEvent {
            // compact constructor
        }
    }
}
