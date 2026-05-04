package com.openclaw.manager.openclawserversmanager.containerlogs.dto;

import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLog;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLogLevel;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLogStream;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;

import java.time.Instant;

public record ContainerLogResponse(
        Long id,
        ContainerService service,
        String containerId,
        String containerName,
        ContainerLogStream stream,
        ContainerLogLevel level,
        String message,
        Instant logTs,
        Instant ingestedAt
) {
    public static ContainerLogResponse from(ContainerLog log) {
        return new ContainerLogResponse(
                log.getId(),
                log.getService(),
                log.getContainerId(),
                log.getContainerName(),
                log.getStream(),
                log.getLevel(),
                log.getMessage(),
                log.getLogTs(),
                log.getIngestedAt()
        );
    }
}
