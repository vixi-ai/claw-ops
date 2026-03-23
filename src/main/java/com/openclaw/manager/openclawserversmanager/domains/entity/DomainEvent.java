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
@Table(name = "domain_events")
public class DomainEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "assignment_id")
    private UUID assignmentId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DomainEventAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DomainEventOutcome outcome;

    @Column(name = "provider_correlation_id", length = 255)
    private String providerCorrelationId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public DomainEvent() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAssignmentId() { return assignmentId; }
    public void setAssignmentId(UUID assignmentId) { this.assignmentId = assignmentId; }

    public UUID getZoneId() { return zoneId; }
    public void setZoneId(UUID zoneId) { this.zoneId = zoneId; }

    public DomainEventAction getAction() { return action; }
    public void setAction(DomainEventAction action) { this.action = action; }

    public DomainEventOutcome getOutcome() { return outcome; }
    public void setOutcome(DomainEventOutcome outcome) { this.outcome = outcome; }

    public String getProviderCorrelationId() { return providerCorrelationId; }
    public void setProviderCorrelationId(String providerCorrelationId) { this.providerCorrelationId = providerCorrelationId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
