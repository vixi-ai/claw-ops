package com.openclaw.manager.openclawserversmanager.domains.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "managed_zones")
public class ManagedZone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "zone_name", nullable = false, length = 255)
    private String zoneName;

    @Column(name = "provider_account_id", nullable = false)
    private UUID providerAccountId;

    @Column(nullable = false)
    private boolean active = false;

    @Column(name = "default_ttl", nullable = false)
    private int defaultTtl = 300;

    @Column(name = "provider_zone_id", length = 255)
    private String providerZoneId;

    @Column(name = "environment_tag", length = 50)
    private String environmentTag;

    @Column(name = "default_for_auto_assign", nullable = false)
    private boolean defaultForAutoAssign = false;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public ManagedZone() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }

    public UUID getProviderAccountId() { return providerAccountId; }
    public void setProviderAccountId(UUID providerAccountId) { this.providerAccountId = providerAccountId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getDefaultTtl() { return defaultTtl; }
    public void setDefaultTtl(int defaultTtl) { this.defaultTtl = defaultTtl; }

    public String getProviderZoneId() { return providerZoneId; }
    public void setProviderZoneId(String providerZoneId) { this.providerZoneId = providerZoneId; }

    public String getEnvironmentTag() { return environmentTag; }
    public void setEnvironmentTag(String environmentTag) { this.environmentTag = environmentTag; }

    public boolean isDefaultForAutoAssign() { return defaultForAutoAssign; }
    public void setDefaultForAutoAssign(boolean defaultForAutoAssign) { this.defaultForAutoAssign = defaultForAutoAssign; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
