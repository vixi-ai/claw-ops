package com.openclaw.manager.openclawserversmanager.monitoring.entity;

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
@Table(name = "health_snapshots")
public class HealthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "server_id", nullable = false, unique = true)
    private UUID serverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_state", nullable = false, length = 20)
    private HealthState overallState = HealthState.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "cpu_state", length = 20)
    private HealthState cpuState = HealthState.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "memory_state", length = 20)
    private HealthState memoryState = HealthState.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "disk_state", length = 20)
    private HealthState diskState = HealthState.UNKNOWN;

    @Column(name = "ssh_reachable")
    private boolean sshReachable = false;

    @Column(name = "last_check_at")
    private Instant lastCheckAt;

    @Column(name = "last_successful_check_at")
    private Instant lastSuccessfulCheckAt;

    @Column(name = "consecutive_failures")
    private int consecutiveFailures = 0;

    @Column(name = "cpu_usage")
    private Double cpuUsage;

    @Column(name = "memory_usage")
    private Double memoryUsage;

    @Column(name = "disk_usage")
    private Double diskUsage;

    @Column(name = "load_1m")
    private Double load1m;

    @Column(name = "uptime_seconds")
    private Long uptimeSeconds;

    @Column(name = "process_count")
    private Integer processCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposed_state", length = 20)
    private HealthState proposedState;

    @Column(name = "consecutive_in_proposed", nullable = false)
    private int consecutiveInProposed = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_state", length = 20)
    private HealthState previousState;

    @Column(name = "state_changed_at")
    private Instant stateChangedAt;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public HealthSnapshot() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getServerId() { return serverId; }
    public void setServerId(UUID serverId) { this.serverId = serverId; }

    public HealthState getOverallState() { return overallState; }
    public void setOverallState(HealthState overallState) { this.overallState = overallState; }

    public HealthState getCpuState() { return cpuState; }
    public void setCpuState(HealthState cpuState) { this.cpuState = cpuState; }

    public HealthState getMemoryState() { return memoryState; }
    public void setMemoryState(HealthState memoryState) { this.memoryState = memoryState; }

    public HealthState getDiskState() { return diskState; }
    public void setDiskState(HealthState diskState) { this.diskState = diskState; }

    public boolean isSshReachable() { return sshReachable; }
    public void setSshReachable(boolean sshReachable) { this.sshReachable = sshReachable; }

    public Instant getLastCheckAt() { return lastCheckAt; }
    public void setLastCheckAt(Instant lastCheckAt) { this.lastCheckAt = lastCheckAt; }

    public Instant getLastSuccessfulCheckAt() { return lastSuccessfulCheckAt; }
    public void setLastSuccessfulCheckAt(Instant lastSuccessfulCheckAt) { this.lastSuccessfulCheckAt = lastSuccessfulCheckAt; }

    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }

    public Double getCpuUsage() { return cpuUsage; }
    public void setCpuUsage(Double cpuUsage) { this.cpuUsage = cpuUsage; }

    public Double getMemoryUsage() { return memoryUsage; }
    public void setMemoryUsage(Double memoryUsage) { this.memoryUsage = memoryUsage; }

    public Double getDiskUsage() { return diskUsage; }
    public void setDiskUsage(Double diskUsage) { this.diskUsage = diskUsage; }

    public Double getLoad1m() { return load1m; }
    public void setLoad1m(Double load1m) { this.load1m = load1m; }

    public Long getUptimeSeconds() { return uptimeSeconds; }
    public void setUptimeSeconds(Long uptimeSeconds) { this.uptimeSeconds = uptimeSeconds; }

    public Integer getProcessCount() { return processCount; }
    public void setProcessCount(Integer processCount) { this.processCount = processCount; }

    public HealthState getProposedState() { return proposedState; }
    public void setProposedState(HealthState proposedState) { this.proposedState = proposedState; }

    public int getConsecutiveInProposed() { return consecutiveInProposed; }
    public void setConsecutiveInProposed(int consecutiveInProposed) { this.consecutiveInProposed = consecutiveInProposed; }

    public HealthState getPreviousState() { return previousState; }
    public void setPreviousState(HealthState previousState) { this.previousState = previousState; }

    public Instant getStateChangedAt() { return stateChangedAt; }
    public void setStateChangedAt(Instant stateChangedAt) { this.stateChangedAt = stateChangedAt; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
