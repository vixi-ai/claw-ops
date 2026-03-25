package com.openclaw.manager.openclawserversmanager.domains.service;

import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.domains.dto.ProvisioningJobResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.AssignmentType;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainAssignment;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProvisioningJob;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProvisioningJobStatus;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProvisioningStep;
import com.openclaw.manager.openclawserversmanager.domains.exception.DomainException;
import com.openclaw.manager.openclawserversmanager.domains.mapper.ProvisioningJobMapper;
import com.openclaw.manager.openclawserversmanager.domains.repository.DomainAssignmentRepository;
import com.openclaw.manager.openclawserversmanager.domains.repository.ProvisioningJobRepository;
import com.openclaw.manager.openclawserversmanager.domains.repository.SslCertificateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProvisioningOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningOrchestrator.class);

    private final ProvisioningJobRepository jobRepository;
    private final DomainAssignmentRepository assignmentRepository;
    private final SslCertificateRepository sslCertificateRepository;
    private final ProvisioningRunner provisioningRunner;

    public ProvisioningOrchestrator(ProvisioningJobRepository jobRepository,
                                     DomainAssignmentRepository assignmentRepository,
                                     SslCertificateRepository sslCertificateRepository,
                                     ProvisioningRunner provisioningRunner) {
        this.jobRepository = jobRepository;
        this.assignmentRepository = assignmentRepository;
        this.sslCertificateRepository = sslCertificateRepository;
        this.provisioningRunner = provisioningRunner;
    }

    /**
     * On startup, mark any RUNNING jobs as FAILED (orphaned from previous app crash).
     */
    @PostConstruct
    @Transactional
    public void cleanupOrphanedJobs() {
        List<ProvisioningJob> orphaned = jobRepository.findByStatus(ProvisioningJobStatus.RUNNING);
        if (!orphaned.isEmpty()) {
            log.warn("Found {} orphaned RUNNING provisioning jobs from previous shutdown — marking as FAILED", orphaned.size());
            for (ProvisioningJob job : orphaned) {
                job.setStatus(ProvisioningJobStatus.FAILED);
                job.setCurrentStep(ProvisioningStep.FAILED_RETRYABLE);
                job.setErrorMessage("App was stopped during provisioning — retry to continue");
                job.setFinishedAt(Instant.now());
                job.appendLog("Marked as failed: app shutdown during provisioning");
                jobRepository.save(job);
            }
        }
    }

    /**
     * Creates a provisioning job and fires async execution. Returns immediately.
     */
    @Transactional
    public ProvisioningJobResponse triggerProvisioning(UUID assignmentId, Integer targetPort, UUID userId) {
        DomainAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Domain assignment " + assignmentId + " not found"));

        if (assignment.getAssignmentType() != AssignmentType.SERVER) {
            throw new DomainException("SSL provisioning is only supported for SERVER-type domain assignments");
        }

        if (assignment.getResourceId() == null) {
            throw new DomainException("Domain assignment has no associated server (resourceId is null)");
        }

        // Check no RUNNING job already exists for this assignment
        if (jobRepository.existsByDomainAssignmentIdAndStatus(assignmentId, ProvisioningJobStatus.RUNNING)) {
            throw new DomainException("A provisioning job is already running for this domain assignment");
        }

        ProvisioningJob job = new ProvisioningJob();
        job.setDomainAssignmentId(assignmentId);
        job.setServerId(assignment.getResourceId());
        job.setCurrentStep(ProvisioningStep.PENDING_DNS);
        job.setStatus(ProvisioningJobStatus.RUNNING);
        job.setTriggeredBy(userId);
        job = jobRepository.saveAndFlush(job);

        log.info("Triggered SSL provisioning job {} for hostname {} on server {}",
                job.getId(), assignment.getHostname(), assignment.getResourceId());

        // Fire async — returns immediately
        provisioningRunner.run(job.getId());

        return ProvisioningJobMapper.toResponse(job, assignment.getHostname());
    }

    /**
     * Retries a FAILED_RETRYABLE job by resetting it and firing again.
     */
    @Transactional
    public ProvisioningJobResponse retryProvisioning(UUID jobId, UUID userId) {
        ProvisioningJob job = findJobOrThrow(jobId);

        if (job.getStatus() != ProvisioningJobStatus.FAILED) {
            throw new DomainException("Only FAILED jobs can be retried (current status: " + job.getStatus() + ")");
        }

        if (job.getCurrentStep() == ProvisioningStep.FAILED_PERMANENT) {
            throw new DomainException("This job failed permanently and cannot be retried");
        }

        if (job.getRetryCount() >= job.getMaxRetries()) {
            throw new DomainException("Maximum retry count (%d) reached".formatted(job.getMaxRetries()));
        }

        DomainAssignment assignment = assignmentRepository.findById(job.getDomainAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Domain assignment not found"));

        job.setStatus(ProvisioningJobStatus.RUNNING);
        job.setCurrentStep(ProvisioningStep.PENDING_DNS);
        job.setRetryCount(job.getRetryCount() + 1);
        job.setErrorMessage(null);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        job.appendLog("Retry #" + job.getRetryCount() + " triggered by user " + userId);
        job = jobRepository.saveAndFlush(job);

        provisioningRunner.run(job.getId());

        return ProvisioningJobMapper.toResponse(job, assignment.getHostname());
    }

    /**
     * Cancels a RUNNING job. The runner checks for cancellation between steps.
     */
    @Transactional
    public void cancelProvisioning(UUID jobId, UUID userId) {
        ProvisioningJob job = findJobOrThrow(jobId);

        if (job.getStatus() != ProvisioningJobStatus.RUNNING) {
            throw new DomainException("Only RUNNING jobs can be cancelled (current status: " + job.getStatus() + ")");
        }

        job.setStatus(ProvisioningJobStatus.CANCELLED);
        job.setFinishedAt(java.time.Instant.now());
        job.appendLog("Cancelled by user " + userId);
        jobRepository.save(job);
    }

    public ProvisioningJobResponse getJob(UUID jobId) {
        ProvisioningJob job = findJobOrThrow(jobId);
        DomainAssignment assignment = assignmentRepository.findById(job.getDomainAssignmentId()).orElse(null);
        String hostname = assignment != null ? assignment.getHostname() : "unknown";
        return ProvisioningJobMapper.toResponse(job, hostname);
    }

    public Optional<ProvisioningJobResponse> getLatestJob(UUID assignmentId) {
        return jobRepository.findFirstByDomainAssignmentIdOrderByCreatedAtDesc(assignmentId)
                .map(job -> {
                    DomainAssignment assignment = assignmentRepository.findById(job.getDomainAssignmentId()).orElse(null);
                    String hostname = assignment != null ? assignment.getHostname() : "unknown";
                    return ProvisioningJobMapper.toResponse(job, hostname);
                });
    }

    public Page<ProvisioningJobResponse> getJobs(UUID serverId, ProvisioningJobStatus status, Pageable pageable) {
        Page<ProvisioningJob> jobs;
        if (serverId != null && status != null) {
            jobs = jobRepository.findByServerIdAndStatus(serverId, status, pageable);
        } else if (serverId != null) {
            jobs = jobRepository.findByServerId(serverId, pageable);
        } else {
            jobs = jobRepository.findAll(pageable);
        }

        return jobs.map(job -> {
            DomainAssignment assignment = assignmentRepository.findById(job.getDomainAssignmentId()).orElse(null);
            String hostname = assignment != null ? assignment.getHostname() : "unknown";
            return ProvisioningJobMapper.toResponse(job, hostname);
        });
    }

    private ProvisioningJob findJobOrThrow(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provisioning job " + id + " not found"));
    }
}
