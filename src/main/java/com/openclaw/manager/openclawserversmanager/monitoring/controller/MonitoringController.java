package com.openclaw.manager.openclawserversmanager.monitoring.controller;

import com.openclaw.manager.openclawserversmanager.monitoring.collector.CollectionResult;
import com.openclaw.manager.openclawserversmanager.monitoring.collector.MetricCollector;
import com.openclaw.manager.openclawserversmanager.monitoring.dto.CreateMaintenanceWindowRequest;
import com.openclaw.manager.openclawserversmanager.monitoring.dto.FleetHealthResponse;
import com.openclaw.manager.openclawserversmanager.monitoring.dto.MaintenanceWindowResponse;
import com.openclaw.manager.openclawserversmanager.monitoring.dto.MetricHistoryResponse;
import com.openclaw.manager.openclawserversmanager.monitoring.dto.MetricPoint;
import com.openclaw.manager.openclawserversmanager.monitoring.dto.MonitoringProfileResponse;
import com.openclaw.manager.openclawserversmanager.monitoring.dto.ServerHealthSummary;
import com.openclaw.manager.openclawserversmanager.monitoring.dto.UpdateMonitoringProfileRequest;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.HealthState;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.MetricSample;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.MetricType;
import com.openclaw.manager.openclawserversmanager.monitoring.service.HealthService;
import com.openclaw.manager.openclawserversmanager.monitoring.service.MaintenanceService;
import com.openclaw.manager.openclawserversmanager.monitoring.service.MetricsService;
import com.openclaw.manager.openclawserversmanager.monitoring.service.MonitoringProfileService;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.repository.ServerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/monitoring")
@Tag(name = "Monitoring")
@SecurityRequirement(name = "bearerAuth")
public class MonitoringController {

    private final HealthService healthService;
    private final MetricsService metricsService;
    private final MonitoringProfileService monitoringProfileService;
    private final MaintenanceService maintenanceService;
    private final MetricCollector metricCollector;
    private final ServerRepository serverRepository;
    private final Executor monitoringExecutor;

    public MonitoringController(HealthService healthService,
                                MetricsService metricsService,
                                MonitoringProfileService monitoringProfileService,
                                MaintenanceService maintenanceService,
                                MetricCollector metricCollector,
                                ServerRepository serverRepository,
                                @Qualifier("monitoringExecutor") Executor monitoringExecutor) {
        this.healthService = healthService;
        this.metricsService = metricsService;
        this.monitoringProfileService = monitoringProfileService;
        this.maintenanceService = maintenanceService;
        this.metricCollector = metricCollector;
        this.serverRepository = serverRepository;
        this.monitoringExecutor = monitoringExecutor;
    }

    // ── Health ──────────────────────────────────────────────

    @GetMapping("/health")
    @Operation(summary = "Get fleet health overview")
    public ResponseEntity<FleetHealthResponse> getFleetHealth(
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) HealthState state) {
        return ResponseEntity.ok(healthService.getFleetHealth(environment, state));
    }

    @GetMapping("/health/{serverId}")
    @Operation(summary = "Get server health detail")
    public ResponseEntity<ServerHealthSummary> getServerHealth(@PathVariable UUID serverId) {
        ServerHealthSummary summary = healthService.getServerHealth(serverId);
        if (summary == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(summary);
    }

    // ── Metrics ─────────────────────────────────────────────

    @GetMapping("/metrics/{serverId}")
    @Operation(summary = "Get metric history for a server")
    public ResponseEntity<MetricHistoryResponse> getMetricHistory(
            @PathVariable UUID serverId,
            @RequestParam MetricType type,
            @RequestParam(required = false) String label,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        if (from == null) from = Instant.now().minus(1, ChronoUnit.HOURS);
        if (to == null) to = Instant.now();

        List<MetricSample> samples = metricsService.queryMetrics(serverId, type, from, to);

        // Filter by label if provided
        if (label != null && !label.isBlank()) {
            samples = samples.stream()
                    .filter(s -> label.equals(s.getMetricLabel()))
                    .toList();
        }

        List<MetricPoint> dataPoints = samples.stream()
                .map(s -> new MetricPoint(s.getCollectedAt(), s.getValue()))
                .toList();

        return ResponseEntity.ok(new MetricHistoryResponse(serverId, type, label, dataPoints));
    }

    @GetMapping("/metrics/{serverId}/latest")
    @Operation(summary = "Get latest metric values for a server")
    public ResponseEntity<List<Map<String, Object>>> getLatestMetrics(@PathVariable UUID serverId) {
        List<MetricSample> samples = metricsService.getLatestMetrics(serverId);
        List<Map<String, Object>> result = samples.stream()
                .map(s -> Map.<String, Object>of(
                        "metricType", s.getMetricType(),
                        "value", s.getValue(),
                        "label", s.getMetricLabel() != null ? s.getMetricLabel() : "",
                        "collectedAt", s.getCollectedAt()
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    // ── Trigger Check ───────────────────────────────────────

    @PostMapping("/check/{serverId}")
    @Operation(summary = "Trigger immediate health check for a server")
    public ResponseEntity<Map<String, String>> triggerCheck(@PathVariable UUID serverId) {
        Server server = serverRepository.findById(serverId).orElse(null);
        if (server == null) {
            return ResponseEntity.notFound().build();
        }

        monitoringExecutor.execute(() -> {
            CollectionResult result = metricCollector.collect(server);
            metricsService.processCollectionResult(result);
        });

        return ResponseEntity.accepted().body(Map.of("message", "Check queued for server " + server.getName()));
    }

    // ── Monitoring Profiles ─────────────────────────────────

    @GetMapping("/profiles/{serverId}")
    @Operation(summary = "Get monitoring profile for a server")
    public ResponseEntity<MonitoringProfileResponse> getProfile(@PathVariable UUID serverId) {
        return ResponseEntity.ok(monitoringProfileService.getProfile(serverId));
    }

    @PatchMapping("/profiles/{serverId}")
    @Operation(summary = "Update monitoring profile thresholds and settings")
    public ResponseEntity<MonitoringProfileResponse> updateProfile(
            @PathVariable UUID serverId,
            @Valid @RequestBody UpdateMonitoringProfileRequest request) {
        return ResponseEntity.ok(monitoringProfileService.updateProfile(serverId, request));
    }

    @PostMapping("/profiles/{serverId}/reset")
    @Operation(summary = "Reset monitoring profile to defaults")
    public ResponseEntity<MonitoringProfileResponse> resetProfile(@PathVariable UUID serverId) {
        return ResponseEntity.ok(monitoringProfileService.resetProfile(serverId));
    }

    // ── Maintenance Windows ─────────────────────────────────

    @GetMapping("/maintenance")
    @Operation(summary = "List maintenance windows")
    public ResponseEntity<List<MaintenanceWindowResponse>> getMaintenanceWindows(
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        if (activeOnly) {
            return ResponseEntity.ok(maintenanceService.getActiveWindows());
        }
        return ResponseEntity.ok(maintenanceService.getAllWindows());
    }

    @PostMapping("/maintenance")
    @Operation(summary = "Create maintenance window")
    public ResponseEntity<MaintenanceWindowResponse> createMaintenanceWindow(
            @Valid @RequestBody CreateMaintenanceWindowRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(maintenanceService.createWindow(request, userId));
    }

    @DeleteMapping("/maintenance/{id}")
    @Operation(summary = "Cancel maintenance window")
    public ResponseEntity<Void> deleteMaintenanceWindow(@PathVariable UUID id) {
        maintenanceService.deleteWindow(id);
        return ResponseEntity.noContent().build();
    }
}
