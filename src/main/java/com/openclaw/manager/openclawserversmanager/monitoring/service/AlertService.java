package com.openclaw.manager.openclawserversmanager.monitoring.service;

import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.*;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.AlertEventRepository;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.AlertRuleRepository;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.NotificationChannelRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AlertService {

    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final NotificationChannelRepository notificationChannelRepository;

    public AlertService(AlertRuleRepository alertRuleRepository,
                        AlertEventRepository alertEventRepository,
                        NotificationChannelRepository notificationChannelRepository) {
        this.alertRuleRepository = alertRuleRepository;
        this.alertEventRepository = alertEventRepository;
        this.notificationChannelRepository = notificationChannelRepository;
    }

    // ── Alert Rules ──

    @Transactional(readOnly = true)
    public List<AlertRule> listRules(UUID serverId, Boolean enabled) {
        if (serverId != null) return alertRuleRepository.findByServerId(serverId);
        if (enabled != null) return alertRuleRepository.findByEnabled(enabled);
        return alertRuleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public AlertRule getRule(UUID id) {
        return alertRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert rule not found: " + id));
    }

    @Transactional
    public AlertRule createRule(String name, String description, UUID serverId,
                                AlertRuleType ruleType, MetricType metricType,
                                ConditionOperator operator, double threshold,
                                IncidentSeverity severity, int consecutiveFailures,
                                int cooldownMinutes, List<UUID> channelIds) {
        if (alertRuleRepository.existsByName(name)) {
            throw new IllegalArgumentException("Alert rule with name '" + name + "' already exists");
        }

        AlertRule rule = new AlertRule();
        rule.setName(name);
        rule.setDescription(description);
        rule.setServerId(serverId);
        rule.setRuleType(ruleType);
        rule.setMetricType(metricType);
        rule.setConditionOperator(operator);
        rule.setThresholdValue(threshold);
        rule.setSeverity(severity);
        rule.setConsecutiveFailures(consecutiveFailures);
        rule.setCooldownMinutes(cooldownMinutes);
        rule.setEnabled(true);

        if (channelIds != null && !channelIds.isEmpty()) {
            List<NotificationChannel> channels = new ArrayList<>();
            for (UUID channelId : channelIds) {
                channels.add(notificationChannelRepository.findById(channelId)
                        .orElseThrow(() -> new ResourceNotFoundException("Channel not found: " + channelId)));
            }
            rule.setNotificationChannels(channels);
        }

        return alertRuleRepository.save(rule);
    }

    @Transactional
    public AlertRule updateRule(UUID id, String name, String description,
                                Boolean enabled, MetricType metricType,
                                ConditionOperator operator, Double threshold,
                                IncidentSeverity severity, Integer consecutiveFailures,
                                Integer cooldownMinutes, List<UUID> channelIds) {
        AlertRule rule = getRule(id);

        if (name != null) {
            if (!name.equals(rule.getName()) && alertRuleRepository.existsByName(name)) {
                throw new IllegalArgumentException("Alert rule with name '" + name + "' already exists");
            }
            rule.setName(name);
        }
        if (description != null) rule.setDescription(description);
        if (enabled != null) rule.setEnabled(enabled);
        if (metricType != null) rule.setMetricType(metricType);
        if (operator != null) rule.setConditionOperator(operator);
        if (threshold != null) rule.setThresholdValue(threshold);
        if (severity != null) rule.setSeverity(severity);
        if (consecutiveFailures != null) rule.setConsecutiveFailures(consecutiveFailures);
        if (cooldownMinutes != null) rule.setCooldownMinutes(cooldownMinutes);

        if (channelIds != null) {
            List<NotificationChannel> channels = new ArrayList<>();
            for (UUID channelId : channelIds) {
                channels.add(notificationChannelRepository.findById(channelId)
                        .orElseThrow(() -> new ResourceNotFoundException("Channel not found: " + channelId)));
            }
            rule.setNotificationChannels(channels);
        }

        return alertRuleRepository.save(rule);
    }

    @Transactional
    public void deleteRule(UUID id) {
        if (!alertRuleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Alert rule not found: " + id);
        }
        alertRuleRepository.deleteById(id);
    }

    // ── Alert Events ──

    @Transactional(readOnly = true)
    public Page<AlertEvent> listEvents(UUID serverId, AlertStatus status, Pageable pageable) {
        if (serverId != null) {
            return alertEventRepository.findByServerIdOrderByFiredAtDesc(serverId, pageable);
        }
        return alertEventRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public AlertEvent getEvent(UUID id) {
        return alertEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert event not found: " + id));
    }

    @Transactional
    public AlertEvent acknowledgeEvent(UUID id, UUID userId) {
        AlertEvent event = getEvent(id);
        if (event.getStatus() != AlertStatus.ACTIVE) {
            throw new IllegalStateException("Can only acknowledge ACTIVE alerts");
        }
        event.setStatus(AlertStatus.ACKNOWLEDGED);
        event.setAcknowledgedBy(userId);
        event.setAcknowledgedAt(Instant.now());
        return alertEventRepository.save(event);
    }

    @Transactional
    public AlertEvent resolveEvent(UUID id) {
        AlertEvent event = getEvent(id);
        if (event.getStatus() == AlertStatus.RESOLVED) {
            throw new IllegalStateException("Alert is already resolved");
        }
        event.setStatus(AlertStatus.RESOLVED);
        event.setResolvedAt(Instant.now());
        return alertEventRepository.save(event);
    }

    @Transactional
    public AlertEvent silenceEvent(UUID id) {
        AlertEvent event = getEvent(id);
        event.setStatus(AlertStatus.SILENCED);
        return alertEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public long countActiveAlerts() {
        return alertEventRepository.countByStatus(AlertStatus.ACTIVE);
    }
}
