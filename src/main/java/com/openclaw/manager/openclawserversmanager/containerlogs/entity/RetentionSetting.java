package com.openclaw.manager.openclawserversmanager.containerlogs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "container_log_retention_settings")
public class RetentionSetting {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContainerService service;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    public RetentionSetting() {
    }

    public RetentionSetting(ContainerService service, int retentionDays) {
        this.service = service;
        this.retentionDays = retentionDays;
    }

    public ContainerService getService() {
        return service;
    }

    public void setService(ContainerService service) {
        this.service = service;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getUpdatedByUserId() {
        return updatedByUserId;
    }

    public void setUpdatedByUserId(UUID updatedByUserId) {
        this.updatedByUserId = updatedByUserId;
    }
}
