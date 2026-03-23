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
@Table(name = "provider_accounts")
public class ProviderAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 30)
    private DnsProviderType providerType;

    @Column(name = "display_name", nullable = false, unique = true, length = 100)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "credential_id", nullable = false)
    private UUID credentialId;

    @Column(name = "provider_settings", columnDefinition = "TEXT")
    private String providerSettings;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 20)
    private HealthStatus healthStatus = HealthStatus.UNKNOWN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public ProviderAccount() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public DnsProviderType getProviderType() { return providerType; }
    public void setProviderType(DnsProviderType providerType) { this.providerType = providerType; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public UUID getCredentialId() { return credentialId; }
    public void setCredentialId(UUID credentialId) { this.credentialId = credentialId; }

    public String getProviderSettings() { return providerSettings; }
    public void setProviderSettings(String providerSettings) { this.providerSettings = providerSettings; }

    public HealthStatus getHealthStatus() { return healthStatus; }
    public void setHealthStatus(HealthStatus healthStatus) { this.healthStatus = healthStatus; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
