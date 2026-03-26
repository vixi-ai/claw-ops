package com.openclaw.manager.openclawserversmanager.monitoring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "monitoring_profiles")
public class MonitoringProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "server_id", nullable = false, unique = true)
    private UUID serverId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "check_interval_seconds", nullable = false)
    private int checkIntervalSeconds = 60;

    @Column(name = "metric_retention_days", nullable = false)
    private int metricRetentionDays = 7;

    @Column(name = "cpu_warning_threshold", precision = 5, scale = 2)
    private BigDecimal cpuWarningThreshold = new BigDecimal("80.00");

    @Column(name = "cpu_critical_threshold", precision = 5, scale = 2)
    private BigDecimal cpuCriticalThreshold = new BigDecimal("95.00");

    @Column(name = "memory_warning_threshold", precision = 5, scale = 2)
    private BigDecimal memoryWarningThreshold = new BigDecimal("80.00");

    @Column(name = "memory_critical_threshold", precision = 5, scale = 2)
    private BigDecimal memoryCriticalThreshold = new BigDecimal("95.00");

    @Column(name = "disk_warning_threshold", precision = 5, scale = 2)
    private BigDecimal diskWarningThreshold = new BigDecimal("85.00");

    @Column(name = "disk_critical_threshold", precision = 5, scale = 2)
    private BigDecimal diskCriticalThreshold = new BigDecimal("95.00");

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public MonitoringProfile() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getServerId() { return serverId; }
    public void setServerId(UUID serverId) { this.serverId = serverId; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getCheckIntervalSeconds() { return checkIntervalSeconds; }
    public void setCheckIntervalSeconds(int checkIntervalSeconds) { this.checkIntervalSeconds = checkIntervalSeconds; }

    public int getMetricRetentionDays() { return metricRetentionDays; }
    public void setMetricRetentionDays(int metricRetentionDays) { this.metricRetentionDays = metricRetentionDays; }

    public BigDecimal getCpuWarningThreshold() { return cpuWarningThreshold; }
    public void setCpuWarningThreshold(BigDecimal cpuWarningThreshold) { this.cpuWarningThreshold = cpuWarningThreshold; }

    public BigDecimal getCpuCriticalThreshold() { return cpuCriticalThreshold; }
    public void setCpuCriticalThreshold(BigDecimal cpuCriticalThreshold) { this.cpuCriticalThreshold = cpuCriticalThreshold; }

    public BigDecimal getMemoryWarningThreshold() { return memoryWarningThreshold; }
    public void setMemoryWarningThreshold(BigDecimal memoryWarningThreshold) { this.memoryWarningThreshold = memoryWarningThreshold; }

    public BigDecimal getMemoryCriticalThreshold() { return memoryCriticalThreshold; }
    public void setMemoryCriticalThreshold(BigDecimal memoryCriticalThreshold) { this.memoryCriticalThreshold = memoryCriticalThreshold; }

    public BigDecimal getDiskWarningThreshold() { return diskWarningThreshold; }
    public void setDiskWarningThreshold(BigDecimal diskWarningThreshold) { this.diskWarningThreshold = diskWarningThreshold; }

    public BigDecimal getDiskCriticalThreshold() { return diskCriticalThreshold; }
    public void setDiskCriticalThreshold(BigDecimal diskCriticalThreshold) { this.diskCriticalThreshold = diskCriticalThreshold; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
