package com.openclaw.manager.openclawserversmanager.domains.controller;

import com.openclaw.manager.openclawserversmanager.domains.dto.AssignCustomDomainRequest;
import com.openclaw.manager.openclawserversmanager.domains.dto.AssignServerDomainRequest;
import com.openclaw.manager.openclawserversmanager.domains.dto.DomainAssignmentResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.DomainEventResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.DomainJobResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainJobStatus;
import com.openclaw.manager.openclawserversmanager.domains.service.DomainAssignmentOrchestrator;
import com.openclaw.manager.openclawserversmanager.domains.service.DomainAssignmentService;
import com.openclaw.manager.openclawserversmanager.domains.service.DomainEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/domain-assignments")
@Tag(name = "domain-assignments", description = "DNS domain assignment management")
@SecurityRequirement(name = "bearerAuth")
public class DomainAssignmentController {

    private final DomainAssignmentService domainAssignmentService;
    private final DomainAssignmentOrchestrator domainAssignmentOrchestrator;
    private final DomainEventService domainEventService;

    public DomainAssignmentController(DomainAssignmentService domainAssignmentService,
                                       DomainAssignmentOrchestrator domainAssignmentOrchestrator,
                                       DomainEventService domainEventService) {
        this.domainAssignmentService = domainAssignmentService;
        this.domainAssignmentOrchestrator = domainAssignmentOrchestrator;
        this.domainEventService = domainEventService;
    }

    @PostMapping("/server")
    @Operation(summary = "Assign a server domain hostname (async — polls job endpoint)")
    public ResponseEntity<DomainAssignmentResponse> assignServerDomain(
            @Valid @RequestBody AssignServerDomainRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(domainAssignmentService.assignServerDomain(request, userId));
    }

    @PostMapping("/custom")
    @Operation(summary = "Assign a custom DNS record (async — polls job endpoint)")
    public ResponseEntity<DomainAssignmentResponse> assignCustomDomain(
            @Valid @RequestBody AssignCustomDomainRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(domainAssignmentService.assignCustomDomain(request, userId));
    }

    @GetMapping
    @Operation(summary = "List domain assignments (filterable by zoneId, resourceId)")
    public ResponseEntity<?> getAssignments(
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID resourceId,
            Pageable pageable) {
        if (zoneId != null) {
            return ResponseEntity.ok(domainAssignmentService.getAssignmentsForZone(zoneId));
        }
        if (resourceId != null) {
            return ResponseEntity.ok(domainAssignmentService.getAssignmentsForResource(resourceId));
        }
        return ResponseEntity.ok(domainAssignmentService.getAllAssignments(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get assignment by ID")
    public ResponseEntity<DomainAssignmentResponse> getAssignment(@PathVariable UUID id) {
        return ResponseEntity.ok(domainAssignmentService.getAssignment(id));
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Verify DNS propagation for assignment (synchronous; includes one quick retry)")
    public ResponseEntity<DomainAssignmentResponse> verifyAssignment(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(domainAssignmentService.verifyAssignment(id, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Release a domain assignment")
    public ResponseEntity<Void> releaseAssignment(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        domainAssignmentService.releaseAssignment(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/resource/{resourceId}")
    @Operation(summary = "Release all assignments for a resource")
    public ResponseEntity<Void> releaseAllForResource(
            @PathVariable UUID resourceId,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        domainAssignmentService.releaseAllForResource(resourceId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "Get assignment event history")
    public ResponseEntity<List<DomainEventResponse>> getAssignmentEvents(@PathVariable UUID id) {
        return ResponseEntity.ok(domainEventService.getEventsForAssignment(id));
    }

    // ── Async domain-assignment jobs ──────────────────────────────

    @GetMapping("/jobs")
    @Operation(summary = "List domain assignment jobs")
    public ResponseEntity<Page<DomainJobResponse>> getJobs(
            @RequestParam(required = false) UUID serverId,
            @RequestParam(required = false) DomainJobStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(domainAssignmentOrchestrator.getJobs(serverId, status, pageable));
    }

    @GetMapping("/jobs/active")
    @Operation(summary = "List all currently running domain assignment jobs")
    public ResponseEntity<List<DomainJobResponse>> getActiveJobs() {
        return ResponseEntity.ok(domainAssignmentOrchestrator.getActiveJobs());
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Get domain assignment job status (poll this endpoint)")
    public ResponseEntity<DomainJobResponse> getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(domainAssignmentOrchestrator.getJob(jobId));
    }

    @PostMapping("/jobs/{jobId}/retry")
    @Operation(summary = "Retry a failed domain assignment job")
    public ResponseEntity<DomainJobResponse> retryJob(
            @PathVariable UUID jobId, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(domainAssignmentOrchestrator.retryAssignment(jobId, userId));
    }

    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(summary = "Cancel a running domain assignment job")
    public ResponseEntity<Void> cancelJob(
            @PathVariable UUID jobId, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        domainAssignmentOrchestrator.cancelAssignment(jobId, userId);
        return ResponseEntity.noContent().build();
    }
}
