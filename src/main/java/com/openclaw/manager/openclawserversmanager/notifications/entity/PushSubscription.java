package com.openclaw.manager.openclawserversmanager.notifications.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 512)
    private String endpoint;

    @Column(name = "key_auth", nullable = false)
    private String keyAuth;

    @Column(name = "key_p256dh", nullable = false)
    private String keyP256dh;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // Getters and setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getKeyAuth() { return keyAuth; }
    public void setKeyAuth(String keyAuth) { this.keyAuth = keyAuth; }

    public String getKeyP256dh() { return keyP256dh; }
    public void setKeyP256dh(String keyP256dh) { this.keyP256dh = keyP256dh; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getProviderId() { return providerId; }
    public void setProviderId(UUID providerId) { this.providerId = providerId; }

    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }

    public Instant getCreatedAt() { return createdAt; }
}
