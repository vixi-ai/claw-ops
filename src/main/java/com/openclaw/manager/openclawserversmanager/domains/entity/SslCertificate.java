package com.openclaw.manager.openclawserversmanager.domains.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ssl_certificates")
public class SslCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "server_id")
    private UUID serverId;

    @Column(name = "assignment_id")
    private UUID assignmentId;

    @Column(nullable = false, length = 255)
    private String hostname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SslStatus status = SslStatus.PENDING;

    @Column(name = "admin_email", length = 255)
    private String adminEmail;

    @Column(name = "target_port", nullable = false)
    private int targetPort = 3000;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_renewed_at")
    private Instant lastRenewedAt;

    @Column(name = "provisioning_job_id")
    private UUID provisioningJobId;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public SslCertificate() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getServerId() { return serverId; }
    public void setServerId(UUID serverId) { this.serverId = serverId; }

    public UUID getAssignmentId() { return assignmentId; }
    public void setAssignmentId(UUID assignmentId) { this.assignmentId = assignmentId; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public SslStatus getStatus() { return status; }
    public void setStatus(SslStatus status) { this.status = status; }

    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }

    public int getTargetPort() { return targetPort; }
    public void setTargetPort(int targetPort) { this.targetPort = targetPort; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getLastRenewedAt() { return lastRenewedAt; }
    public void setLastRenewedAt(Instant lastRenewedAt) { this.lastRenewedAt = lastRenewedAt; }

    public UUID getProvisioningJobId() { return provisioningJobId; }
    public void setProvisioningJobId(UUID provisioningJobId) { this.provisioningJobId = provisioningJobId; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
