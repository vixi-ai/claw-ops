package com.openclaw.manager.openclawserversmanager.deployment.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.DeploymentException;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.deployment.dto.DeploymentJobResponse;
import com.openclaw.manager.openclawserversmanager.deployment.dto.TriggerDeploymentRequest;
import com.openclaw.manager.openclawserversmanager.deployment.entity.DeploymentJob;
import com.openclaw.manager.openclawserversmanager.deployment.entity.DeploymentScript;
import com.openclaw.manager.openclawserversmanager.deployment.entity.DeploymentStatus;
import com.openclaw.manager.openclawserversmanager.deployment.mapper.DeploymentMapper;
import com.openclaw.manager.openclawserversmanager.deployment.repository.DeploymentJobRepository;
import com.openclaw.manager.openclawserversmanager.servers.service.ServerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeploymentJobService {

    private final DeploymentJobRepository jobRepository;
    private final DeploymentScriptService scriptService;
    private final ServerService serverService;
    private final ScriptRunner scriptRunner;
    private final InteractiveDeploymentService interactiveDeploymentService;
    private final AuditService auditService;

    public DeploymentJobService(DeploymentJobRepository jobRepository,
                                DeploymentScriptService scriptService,
                                ServerService serverService,
                                ScriptRunner scriptRunner,
                                InteractiveDeploymentService interactiveDeploymentService,
                                AuditService auditService) {
        this.jobRepository = jobRepository;
        this.scriptService = scriptService;
        this.serverService = serverService;
        this.scriptRunner = scriptRunner;
        this.interactiveDeploymentService = interactiveDeploymentService;
        this.auditService = auditService;
    }

    @Transactional
    public DeploymentJobResponse triggerJob(TriggerDeploymentRequest request, UUID userId) {
        // Validate server exists
        serverService.getServerEntity(request.serverId());

        // Validate script exists
        DeploymentScript script = scriptService.getScriptEntity(request.scriptId());

        // Check for running job on this server
        if (jobRepository.existsByServerIdAndStatus(request.serverId(), DeploymentStatus.RUNNING)) {
            throw new DeploymentException("Server already has a running deployment job");
        }

        DeploymentJob job = new DeploymentJob();
        job.setServerId(request.serverId());
        job.setScriptId(script.getId());
        job.setScriptName(script.getName());
        job.setTriggeredBy(userId);
        job.setStatus(DeploymentStatus.PENDING);
        job.setInteractive(request.interactive());
        DeploymentJob saved = jobRepository.save(job);

        if (request.interactive()) {
            // Start interactive deployment with terminal session
            interactiveDeploymentService.startInteractiveDeployment(
                    saved.getId(), request.serverId(), userId, script.getScriptContent());
        } else {
            // Fire async execution (non-interactive)
            scriptRunner.run(saved.getId(), request.serverId(), script.getScriptContent());
        }

        try {
            auditService.log(AuditAction.JOB_TRIGGERED, "DEPLOYMENT_JOB", saved.getId(), userId,
                    "Job triggered: script '%s' on server %s".formatted(script.getName(), request.serverId()));
        } catch (Exception ignored) {}

        return DeploymentMapper.toJobResponse(saved);
    }

    @Transactional
    public DeploymentJobResponse triggerTemplateJob(UUID serverId, String templateName, String installScript, UUID userId) {
        // Validate server exists
        serverService.getServerEntity(serverId);

        if (jobRepository.existsByServerIdAndStatus(serverId, DeploymentStatus.RUNNING)) {
            throw new DeploymentException("Server already has a running deployment job");
        }

        DeploymentJob job = new DeploymentJob();
        job.setServerId(serverId);
        job.setScriptName("template:" + templateName);
        job.setTriggeredBy(userId);
        job.setStatus(DeploymentStatus.PENDING);
        DeploymentJob saved = jobRepository.save(job);

        scriptRunner.run(saved.getId(), serverId, installScript);

        return DeploymentMapper.toJobResponse(saved);
    }

    public Page<DeploymentJobResponse> getJobs(UUID serverId, DeploymentStatus status, Pageable pageable) {
        if (serverId != null && status != null) {
            return jobRepository.findByServerIdAndStatus(serverId, status, pageable)
                    .map(DeploymentMapper::toJobResponse);
        } else if (serverId != null) {
            return jobRepository.findByServerId(serverId, pageable)
                    .map(DeploymentMapper::toJobResponse);
        } else if (status != null) {
            return jobRepository.findByStatus(status, pageable)
                    .map(DeploymentMapper::toJobResponse);
        }
        return jobRepository.findAll(pageable).map(DeploymentMapper::toJobResponse);
    }

    public DeploymentJobResponse getJob(UUID id) {
        return DeploymentMapper.toJobResponse(findOrThrow(id));
    }

    @Transactional
    public DeploymentJobResponse cancelJob(UUID id, UUID userId) {
        DeploymentJob job = findOrThrow(id);

        if (job.getStatus() == DeploymentStatus.RUNNING) {
            throw new DeploymentException("Cannot cancel a running job");
        }
        if (job.getStatus() != DeploymentStatus.PENDING) {
            throw new DeploymentException("Can only cancel PENDING jobs, current status: " + job.getStatus());
        }

        job.setStatus(DeploymentStatus.CANCELLED);
        DeploymentJob saved = jobRepository.save(job);

        try {
            auditService.log(AuditAction.JOB_CANCELLED, "DEPLOYMENT_JOB", id, userId,
                    "Job cancelled");
        } catch (Exception ignored) {}

        return DeploymentMapper.toJobResponse(saved);
    }

    private DeploymentJob findOrThrow(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment job with id " + id + " not found"));
    }
}
