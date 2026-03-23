package com.openclaw.manager.openclawserversmanager.deployment.dto;

import com.openclaw.manager.openclawserversmanager.deployment.entity.DeploymentStatus;

import java.time.Instant;
import java.util.UUID;

public record DeploymentJobResponse(
        UUID id,
        UUID serverId,
        UUID scriptId,
        String scriptName,
        DeploymentStatus status,
        UUID triggeredBy,
        Instant startedAt,
        Instant finishedAt,
        String logs,
        String errorMessage,
        Instant createdAt
) {}
