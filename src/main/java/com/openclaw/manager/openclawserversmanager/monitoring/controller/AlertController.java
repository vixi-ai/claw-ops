package com.openclaw.manager.openclawserversmanager.monitoring.controller;

import com.openclaw.manager.openclawserversmanager.monitoring.dto.*;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.AlertEvent;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.AlertRule;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.AlertStatus;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.AlertRuleRepository;
import com.openclaw.manager.openclawserversmanager.monitoring.service.AlertService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/monitoring/alerts")
@SecurityRequirement(name = "bearerAuth")
public class AlertController {

    private final AlertService alertService;
    private final AlertRuleRepository alertRuleRepository;

    public AlertController(AlertService alertService, AlertRuleRepository alertRuleRepository) {
        this.alertService = alertService;
        this.alertRuleRepository = alertRuleRepository;
    }

    // ── Alert Rules ──

    @GetMapping("/rules")
    public ResponseEntity<?> listRules(
            @RequestParam(required = false) UUID serverId,
            @RequestParam(required = false) Boolean enabled) {
        return ResponseEntity.ok(alertService.listRules(serverId, enabled).stream()
                .map(AlertRuleResponse::from)
                .toList());
    }

    @PostMapping("/rules")
    public ResponseEntity<AlertRuleResponse> createRule(@Valid @RequestBody CreateAlertRuleRequest req) {
        AlertRule rule = alertService.createRule(
                req.name(), req.description(), req.serverId(),
                req.ruleType(), req.metricType(), req.conditionOperator(),
                req.thresholdValue(), req.severity(),
                req.consecutiveFailures(), req.cooldownMinutes(),
                req.channelIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(AlertRuleResponse.from(rule));
    }

    @GetMapping("/rules/{id}")
    public ResponseEntity<AlertRuleResponse> getRule(@PathVariable UUID id) {
        return ResponseEntity.ok(AlertRuleResponse.from(alertService.getRule(id)));
    }

    @PatchMapping("/rules/{id}")
    public ResponseEntity<AlertRuleResponse> updateRule(@PathVariable UUID id,
                                                         @Valid @RequestBody UpdateAlertRuleRequest req) {
        AlertRule rule = alertService.updateRule(id,
                req.name(), req.description(), req.enabled(),
                req.metricType(), req.conditionOperator(), req.thresholdValue(),
                req.severity(), req.consecutiveFailures(), req.cooldownMinutes(),
                req.channelIds());
        return ResponseEntity.ok(AlertRuleResponse.from(rule));
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRule(@PathVariable UUID id) {
        alertService.deleteRule(id);
    }

    // ── Alert Events ──

    @GetMapping("/events")
    public ResponseEntity<Page<AlertEventResponse>> listEvents(
            @RequestParam(required = false) UUID serverId,
            @RequestParam(required = false) AlertStatus status,
            Pageable pageable) {
        Page<AlertEvent> events = alertService.listEvents(serverId, status, pageable);
        Page<AlertEventResponse> response = events.map(e -> {
            String ruleName = alertRuleRepository.findById(e.getAlertRuleId())
                    .map(AlertRule::getName)
                    .orElse("Unknown");
            return AlertEventResponse.from(e, ruleName);
        });
        return ResponseEntity.ok(response);
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<AlertEventResponse> getEvent(@PathVariable UUID id) {
        AlertEvent event = alertService.getEvent(id);
        String ruleName = alertRuleRepository.findById(event.getAlertRuleId())
                .map(AlertRule::getName)
                .orElse("Unknown");
        return ResponseEntity.ok(AlertEventResponse.from(event, ruleName));
    }

    @PostMapping("/events/{id}/acknowledge")
    public ResponseEntity<AlertEventResponse> acknowledgeEvent(@PathVariable UUID id,
                                                                 Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        AlertEvent event = alertService.acknowledgeEvent(id, userId);
        String ruleName = alertRuleRepository.findById(event.getAlertRuleId())
                .map(AlertRule::getName)
                .orElse("Unknown");
        return ResponseEntity.ok(AlertEventResponse.from(event, ruleName));
    }

    @PostMapping("/events/{id}/resolve")
    public ResponseEntity<AlertEventResponse> resolveEvent(@PathVariable UUID id) {
        AlertEvent event = alertService.resolveEvent(id);
        String ruleName = alertRuleRepository.findById(event.getAlertRuleId())
                .map(AlertRule::getName)
                .orElse("Unknown");
        return ResponseEntity.ok(AlertEventResponse.from(event, ruleName));
    }

    @PostMapping("/events/{id}/silence")
    public ResponseEntity<AlertEventResponse> silenceEvent(@PathVariable UUID id) {
        AlertEvent event = alertService.silenceEvent(id);
        String ruleName = alertRuleRepository.findById(event.getAlertRuleId())
                .map(AlertRule::getName)
                .orElse("Unknown");
        return ResponseEntity.ok(AlertEventResponse.from(event, ruleName));
    }

    @GetMapping("/events/count")
    public ResponseEntity<Map<String, Long>> countActiveAlerts() {
        return ResponseEntity.ok(Map.of("active", alertService.countActiveAlerts()));
    }
}
