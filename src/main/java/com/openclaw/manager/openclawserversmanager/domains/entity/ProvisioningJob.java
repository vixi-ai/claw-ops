package com.openclaw.manager.openclawserversmanager.domains.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provisioning_jobs")
public class ProvisioningJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "domain_assignment_id", nullable = false)
    private UUID domainAssignmentId;

    @Column(name = "server_id", nullable = false)
    private UUID serverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 30)
    private ProvisioningStep currentStep = ProvisioningStep.PENDING_DNS;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProvisioningJobStatus status = ProvisioningJobStatus.RUNNING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    @Column(columnDefinition = "TEXT")
    private String logs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "acme_txt_record_id", length = 255)
    private String acmeTxtRecordId;

    @Column(name = "triggered_by")
    private UUID triggeredBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public ProvisioningJob() {}

    public void appendLog(String message) {
        String timestamp = Instant.now().toString();
        String entry = "[%s] %s\n".formatted(timestamp, message);
        this.logs = (this.logs == null ? "" : this.logs) + entry;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDomainAssignmentId() { return domainAssignmentId; }
    public void setDomainAssignmentId(UUID domainAssignmentId) { this.domainAssignmentId = domainAssignmentId; }

    public UUID getServerId() { return serverId; }
    public void setServerId(UUID serverId) { this.serverId = serverId; }

    public ProvisioningStep getCurrentStep() { return currentStep; }
    public void setCurrentStep(ProvisioningStep currentStep) { this.currentStep = currentStep; }

    public ProvisioningJobStatus getStatus() { return status; }
    public void setStatus(ProvisioningJobStatus status) { this.status = status; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public String getLogs() { return logs; }
    public void setLogs(String logs) { this.logs = logs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getAcmeTxtRecordId() { return acmeTxtRecordId; }
    public void setAcmeTxtRecordId(String acmeTxtRecordId) { this.acmeTxtRecordId = acmeTxtRecordId; }

    public UUID getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(UUID triggeredBy) { this.triggeredBy = triggeredBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
