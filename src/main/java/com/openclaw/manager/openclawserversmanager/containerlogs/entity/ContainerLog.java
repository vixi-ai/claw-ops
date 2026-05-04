package com.openclaw.manager.openclawserversmanager.containerlogs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "container_logs")
public class ContainerLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContainerService service;

    @Column(name = "container_id", nullable = false, length = 64)
    private String containerId;

    @Column(name = "container_name", nullable = false, length = 128)
    private String containerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ContainerLogStream stream;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ContainerLogLevel level;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "log_ts", nullable = false)
    private Instant logTs;

    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt = Instant.now();

    public ContainerLog() {
    }

    public ContainerLog(ContainerService service, String containerId, String containerName,
                        ContainerLogStream stream, ContainerLogLevel level, String message, Instant logTs) {
        this.service = service;
        this.containerId = containerId;
        this.containerName = containerName;
        this.stream = stream;
        this.level = level;
        this.message = message;
        this.logTs = logTs;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ContainerService getService() {
        return service;
    }

    public void setService(ContainerService service) {
        this.service = service;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public ContainerLogStream getStream() {
        return stream;
    }

    public void setStream(ContainerLogStream stream) {
        this.stream = stream;
    }

    public ContainerLogLevel getLevel() {
        return level;
    }

    public void setLevel(ContainerLogLevel level) {
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getLogTs() {
        return logTs;
    }

    public void setLogTs(Instant logTs) {
        this.logTs = logTs;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }

    public void setIngestedAt(Instant ingestedAt) {
        this.ingestedAt = ingestedAt;
    }
}
