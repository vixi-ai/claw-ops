package com.openclaw.manager.openclawserversmanager.domains.service;

import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.domains.dto.DomainJobResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainAssignment;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainAssignmentJob;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainJobStatus;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainJobStep;
import com.openclaw.manager.openclawserversmanager.domains.exception.DomainException;
import com.openclaw.manager.openclawserversmanager.domains.mapper.DomainAssignmentJobMapper;
import com.openclaw.manager.openclawserversmanager.domains.repository.DomainAssignmentJobRepository;
import com.openclaw.manager.openclawserversmanager.domains.repository.DomainAssignmentRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the lifecycle of {@link DomainAssignmentJob}s: trigger (async), retry, cancel,
 * and query. Mirrors {@link ProvisioningOrchestrator}'s pattern for SSL.
 */
@Service
public class DomainAssignmentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DomainAssignmentOrchestrator.class);

    private final DomainAssignmentJobRepository jobRepository;
    private final DomainAssignmentRepository assignmentRepository;
    private final DomainAssignmentRunner runner;

    public DomainAssignmentOrchestrator(DomainAssignmentJobRepository jobRepository,
                                        DomainAssignmentRepository assignmentRepository,
                                        DomainAssignmentRunner runner) {
        this.jobRepository = jobRepository;
        this.assignmentRepository = assignmentRepository;
        this.runner = runner;
    }

    /** On startup, mark any RUNNING jobs as FAILED_RETRYABLE (orphaned from prior shutdown). */
    @PostConstruct
    @Transactional
    public void cleanupOrphanedJobs() {
        List<DomainAssignmentJob> orphaned = jobRepository.findByStatus(DomainJobStatus.RUNNING);
        if (orphaned.isEmpty()) return;
        log.warn("Found {} orphaned RUNNING domain assignment jobs from previous shutdown — marking as FAILED",
                orphaned.size());
        for (DomainAssignmentJob job : orphaned) {
            job.setStatus(DomainJobStatus.FAILED);
            job.setCurrentStep(DomainJobStep.FAILED_RETRYABLE);
            job.setErrorMessage("App was stopped during domain assignment — retry to continue");
            job.setFinishedAt(Instant.now());
            job.appendLog("Marked as failed: app shutdown during processing");
            jobRepository.save(job);
        }
    }

    /** Create a job and fire the async runner. Returns immediately. */
    @Transactional
    public DomainJobResponse triggerAssignment(UUID assignmentId, UUID userId) {
        DomainAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Domain assignment " + assignmentId + " not found"));

        if (jobRepository.existsByDomainAssignmentIdAndStatus(assignmentId, DomainJobStatus.RUNNING)) {
            throw new DomainException("A domain assignment job is already running for this assignment");
        }

        DomainAssignmentJob job = new DomainAssignmentJob();
        job.setDomainAssignmentId(assignmentId);
        job.setServerId(assignment.getResourceId());
        job.setCurrentStep(DomainJobStep.PENDING_DNS);
        job.setStatus(DomainJobStatus.RUNNING);
        job.setTriggeredBy(userId);
        job = jobRepository.saveAndFlush(job);

        log.info("Triggered domain assignment job {} for hostname {} (server {})",
                job.getId(), assignment.getHostname(), assignment.getResourceId());

        runner.run(job.getId());
        return DomainAssignmentJobMapper.toResponse(job, assignment.getHostname());
    }

    @Transactional
    public DomainJobResponse retryAssignment(UUID jobId, UUID userId) {
        DomainAssignmentJob job = findJobOrThrow(jobId);

        if (job.getStatus() != DomainJobStatus.FAILED) {
            throw new DomainException("Only FAILED jobs can be retried (current status: " + job.getStatus() + ")");
        }
        if (job.getCurrentStep() == DomainJobStep.FAILED_PERMANENT) {
            throw new DomainException("This job failed permanently and cannot be retried");
        }
        if (job.getRetryCount() >= job.getMaxRetries()) {
            throw new DomainException("Maximum retry count (%d) reached".formatted(job.getMaxRetries()));
        }

        DomainAssignment assignment = assignmentRepository.findById(job.getDomainAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Domain assignment not found"));

        job.setStatus(DomainJobStatus.RUNNING);
        job.setCurrentStep(DomainJobStep.PENDING_DNS);
        job.setRetryCount(job.getRetryCount() + 1);
        job.setErrorMessage(null);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        job.appendLog("Retry #" + job.getRetryCount() + " triggered by user " + userId);
        job = jobRepository.saveAndFlush(job);

        runner.run(job.getId());
        return DomainAssignmentJobMapper.toResponse(job, assignment.getHostname());
    }

    @Transactional
    public void cancelAssignment(UUID jobId, UUID userId) {
        DomainAssignmentJob job = findJobOrThrow(jobId);
        if (job.getStatus() != DomainJobStatus.RUNNING) {
            throw new DomainException("Only RUNNING jobs can be cancelled (current status: "
                    + job.getStatus() + ")");
        }
        job.setStatus(DomainJobStatus.CANCELLED);
        job.setFinishedAt(Instant.now());
        job.appendLog("Cancelled by user " + userId);
        jobRepository.save(job);
    }

    public DomainJobResponse getJob(UUID jobId) {
        DomainAssignmentJob job = findJobOrThrow(jobId);
        String hostname = hostnameFor(job);
        return DomainAssignmentJobMapper.toResponse(job, hostname);
    }

    public Optional<DomainJobResponse> getLatestJobForAssignment(UUID assignmentId) {
        return jobRepository.findFirstByDomainAssignmentIdOrderByCreatedAtDesc(assignmentId)
                .map(job -> DomainAssignmentJobMapper.toResponse(job, hostnameFor(job)));
    }

    public Optional<DomainJobResponse> getLatestJobForServer(UUID serverId) {
        return jobRepository.findFirstByServerIdOrderByCreatedAtDesc(serverId)
                .map(job -> DomainAssignmentJobMapper.toResponse(job, hostnameFor(job)));
    }

    public List<DomainJobResponse> getActiveJobs() {
        return jobRepository.findByStatusIn(List.of(DomainJobStatus.RUNNING)).stream()
                .map(job -> DomainAssignmentJobMapper.toResponse(job, hostnameFor(job)))
                .toList();
    }

    public Page<DomainJobResponse> getJobs(UUID serverId, DomainJobStatus status, Pageable pageable) {
        Page<DomainAssignmentJob> jobs;
        if (serverId != null && status != null) {
            jobs = jobRepository.findByServerIdAndStatus(serverId, status, pageable);
        } else if (serverId != null) {
            jobs = jobRepository.findByServerId(serverId, pageable);
        } else {
            jobs = jobRepository.findAll(pageable);
        }
        return jobs.map(job -> DomainAssignmentJobMapper.toResponse(job, hostnameFor(job)));
    }

    private String hostnameFor(DomainAssignmentJob job) {
        return assignmentRepository.findById(job.getDomainAssignmentId())
                .map(DomainAssignment::getHostname)
                .orElse("unknown");
    }

    private DomainAssignmentJob findJobOrThrow(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Domain assignment job " + id + " not found"));
    }
}
