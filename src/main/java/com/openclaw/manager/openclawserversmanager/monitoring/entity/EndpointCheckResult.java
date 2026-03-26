package com.openclaw.manager.openclawserversmanager.monitoring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "endpoint_check_results")
public class EndpointCheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "endpoint_check_id", nullable = false)
    private UUID endpointCheckId;

    @Column(name = "is_up", nullable = false)
    private boolean isUp;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "ssl_expires_at")
    private Instant sslExpiresAt;

    @Column(name = "ssl_days_remaining")
    private Integer sslDaysRemaining;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt = Instant.now();

    public EndpointCheckResult() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEndpointCheckId() { return endpointCheckId; }
    public void setEndpointCheckId(UUID endpointCheckId) { this.endpointCheckId = endpointCheckId; }

    public boolean isUp() { return isUp; }
    public void setUp(boolean up) { isUp = up; }

    public Long getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(Long responseTimeMs) { this.responseTimeMs = responseTimeMs; }

    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }

    public Instant getSslExpiresAt() { return sslExpiresAt; }
    public void setSslExpiresAt(Instant sslExpiresAt) { this.sslExpiresAt = sslExpiresAt; }

    public Integer getSslDaysRemaining() { return sslDaysRemaining; }
    public void setSslDaysRemaining(Integer sslDaysRemaining) { this.sslDaysRemaining = sslDaysRemaining; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant checkedAt) { this.checkedAt = checkedAt; }
}
