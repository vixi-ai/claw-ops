package com.openclaw.manager.openclawserversmanager.deployment.controller;

import com.openclaw.manager.openclawserversmanager.common.exception.DeploymentException;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.deployment.dto.DeploymentJobResponse;
import com.openclaw.manager.openclawserversmanager.deployment.dto.TerminalTokenResponse;
import com.openclaw.manager.openclawserversmanager.deployment.dto.TriggerDeploymentRequest;
import com.openclaw.manager.openclawserversmanager.deployment.entity.DeploymentStatus;
import com.openclaw.manager.openclawserversmanager.deployment.service.DeploymentJobService;
import com.openclaw.manager.openclawserversmanager.deployment.service.InteractiveDeploymentService;
import com.openclaw.manager.openclawserversmanager.terminal.service.TerminalSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deployment-jobs")
@Tag(name = "Deployment Jobs", description = "Trigger and monitor deployment jobs")
@SecurityRequirement(name = "bearerAuth")
public class DeploymentJobController {

    private final DeploymentJobService jobService;
    private final InteractiveDeploymentService interactiveDeploymentService;
    private final TerminalSessionService terminalSessionService;

    public DeploymentJobController(DeploymentJobService jobService,
                                    InteractiveDeploymentService interactiveDeploymentService,
                                    TerminalSessionService terminalSessionService) {
        this.jobService = jobService;
        this.interactiveDeploymentService = interactiveDeploymentService;
        this.terminalSessionService = terminalSessionService;
    }

    @PostMapping
    @Operation(summary = "Trigger a deployment job")
    public ResponseEntity<DeploymentJobResponse> triggerJob(@Valid @RequestBody TriggerDeploymentRequest request,
                                                            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobService.triggerJob(request, userId));
    }

    @GetMapping
    @Operation(summary = "List deployment jobs (filterable by serverId and status)")
    public ResponseEntity<Page<DeploymentJobResponse>> getJobs(
            @RequestParam(required = false) UUID serverId,
            @RequestParam(required = false) DeploymentStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(jobService.getJobs(serverId, status, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a deployment job by ID")
    public ResponseEntity<DeploymentJobResponse> getJob(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.getJob(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a PENDING deployment job")
    public ResponseEntity<DeploymentJobResponse> cancelJob(@PathVariable UUID id,
                                                           Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(jobService.cancelJob(id, userId));
    }

    @PostMapping("/{id}/stop")
    @Operation(summary = "Stop a running interactive deployment job")
    public ResponseEntity<DeploymentJobResponse> stopJob(@PathVariable UUID id,
                                                          Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        interactiveDeploymentService.stopDeployment(id);
        return ResponseEntity.ok(jobService.getJob(id));
    }

    @PostMapping("/{id}/terminal-token")
    @Operation(summary = "Get a terminal session token for an interactive deployment job")
    public ResponseEntity<TerminalTokenResponse> getTerminalToken(@PathVariable UUID id,
                                                                    Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        DeploymentJobResponse job = jobService.getJob(id);

        if (!job.interactive()) {
            throw new DeploymentException("Job is not interactive");
        }
        if (job.status() != DeploymentStatus.RUNNING) {
            throw new DeploymentException("Job is not running (status: " + job.status() + ")");
        }

        // Check if there's an active session to reconnect to
        String existingSessionId = interactiveDeploymentService.getActiveSessionId(id);
        if (existingSessionId != null) {
            String token = terminalSessionService.generateReconnectionToken(
                    job.serverId(), userId, id, existingSessionId);
            return ResponseEntity.ok(new TerminalTokenResponse(token, existingSessionId));
        }

        // Generate a deployment token for new connection
        String token = terminalSessionService.generateDeploymentToken(job.serverId(), userId, id);
        return ResponseEntity.ok(new TerminalTokenResponse(token, job.terminalSessionId()));
    }
}
