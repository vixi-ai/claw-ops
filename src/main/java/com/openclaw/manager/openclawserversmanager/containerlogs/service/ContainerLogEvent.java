package com.openclaw.manager.openclawserversmanager.containerlogs.service;

import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLogLevel;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLogStream;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;

import java.time.Instant;

public record ContainerLogEvent(
        ContainerService service,
        String containerId,
        String containerName,
        ContainerLogStream stream,
        ContainerLogLevel level,
        String message,
        Instant logTs
) {
}
