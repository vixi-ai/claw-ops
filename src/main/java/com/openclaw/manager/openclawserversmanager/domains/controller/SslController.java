package com.openclaw.manager.openclawserversmanager.domains.controller;

import com.openclaw.manager.openclawserversmanager.audit.dto.AuditLogResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.ProvisioningJobResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.SslCertificateResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.SslDashboardResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.SslProbeResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.SslSchedulerStatus;
import com.openclaw.manager.openclawserversmanager.domains.dto.TriggerProvisioningRequest;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProvisioningJobStatus;
import com.openclaw.manager.openclawserversmanager.domains.scheduler.SslRenewalScheduler;
import com.openclaw.manager.openclawserversmanager.domains.service.ProvisioningOrchestrator;
import com.openclaw.manager.openclawserversmanager.domains.service.SslService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ssl-certificates")
@Tag(name = "SSL Certificates", description = "SSL certificate provisioning and management")
public class SslController {

    private final SslService sslService;
    private final ProvisioningOrchestrator provisioningOrchestrator;
    private final SslRenewalScheduler sslRenewalScheduler;

    public SslController(SslService sslService,
                         ProvisioningOrchestrator provisioningOrchestrator,
                         SslRenewalScheduler sslRenewalScheduler) {
        this.sslService = sslService;
        this.provisioningOrchestrator = provisioningOrchestrator;
        this.sslRenewalScheduler = sslRenewalScheduler;
    }

    // ── Provisioning (async) ──────────────────────────────

    @PostMapping
    @Operation(summary = "Trigger async SSL provisioning for a domain assignment")
    public ResponseEntity<ProvisioningJobResponse> provision(
            @Valid @RequestBody TriggerProvisioningRequest request, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        ProvisioningJobResponse job = provisioningOrchestrator.triggerProvisioning(
                request.assignmentId(), request.targetPort(), userId);
        return ResponseEntity.accepted().body(job);
    }

    @GetMapping("/jobs")
    @Operation(summary = "List provisioning jobs")
    public ResponseEntity<Page<ProvisioningJobResponse>> getJobs(
            @RequestParam(required = false) UUID serverId,
            @RequestParam(required = false) ProvisioningJobStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(provisioningOrchestrator.getJobs(serverId, status, pageable));
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Get provisioning job status (poll this endpoint)")
    public ResponseEntity<ProvisioningJobResponse> getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(provisioningOrchestrator.getJob(jobId));
    }

    @PostMapping("/jobs/{jobId}/retry")
    @Operation(summary = "Retry a failed provisioning job")
    public ResponseEntity<ProvisioningJobResponse> retryJob(
            @PathVariable UUID jobId, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(provisioningOrchestrator.retryProvisioning(jobId, userId));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(summary = "Cancel a running provisioning job")
    public ResponseEntity<Void> cancelJob(
            @PathVariable UUID jobId, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        provisioningOrchestrator.cancelProvisioning(jobId, userId);
        return ResponseEntity.noContent().build();
    }

    // ── Dashboard ──────────────────────────────

    @GetMapping("/dashboard")
    @Operation(summary = "SSL certificate dashboard with status summary")
    public ResponseEntity<SslDashboardResponse> dashboard() {
        return ResponseEntity.ok(sslService.getDashboard());
    }

    // ── Certificate queries ──────────────────────────────

    @GetMapping
    @Operation(summary = "List all SSL certificates")
    public ResponseEntity<Page<SslCertificateResponse>> getAll(Pageable pageable) {
        return ResponseEntity.ok(sslService.getAllCertificates(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get SSL certificate by ID")
    public ResponseEntity<SslCertificateResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(sslService.getCertificate(id));
    }

    @GetMapping("/server/{serverId}")
    @Operation(summary = "Get SSL certificate for a server")
    public ResponseEntity<SslCertificateResponse> getForServer(@PathVariable UUID serverId) {
        return sslService.getCertificateForServer(serverId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-assignment/{assignmentId}")
    @Operation(summary = "Get SSL certificate for a domain assignment")
    public ResponseEntity<SslCertificateResponse> getByAssignment(@PathVariable UUID assignmentId) {
        return sslService.getCertificateByAssignment(assignmentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Live-wire probe ──────────────────────────────

    @GetMapping("/{id}/probe")
    @Operation(summary = "Live TLS probe (HTTP+HTTPS reachability + wire-side cert expiry). Read-only.")
    public ResponseEntity<SslProbeResponse> probe(@PathVariable UUID id) {
        return ResponseEntity.ok(sslService.probe(id));
    }

    // ── Audit trail ──────────────────────────────

    @GetMapping("/{id}/audit-log")
    @Operation(summary = "Per-certificate audit history (provisioned/renewed/checked/removed events)")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLog(@PathVariable UUID id, Pageable pageable) {
        return ResponseEntity.ok(sslService.getAuditLog(id, pageable));
    }

    // ── Auto-renewal transparency ──────────────────────────────

    @GetMapping("/scheduler-status")
    @Operation(summary = "Auto-renewal scheduler: last run timestamp, last outcome, next run")
    public ResponseEntity<SslSchedulerStatus> getSchedulerStatus() {
        return ResponseEntity.ok(sslRenewalScheduler.getStatus());
    }

    // ── Certificate operations ──────────────────────────────

    @PostMapping("/{id}/renew")
    @Operation(summary = "Renew SSL certificate")
    public ResponseEntity<SslCertificateResponse> renew(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(sslService.renew(id, userId));
    }

    @PostMapping("/{id}/check")
    @Operation(summary = "Check SSL certificate status")
    public ResponseEntity<SslCertificateResponse> check(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(sslService.check(id, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove SSL certificate")
    public ResponseEntity<Void> remove(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        sslService.remove(id, userId);
        return ResponseEntity.noContent().build();
    }
}
