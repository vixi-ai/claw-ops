package com.openclaw.manager.openclawserversmanager.containerlogs.dto;

import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLogLevel;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLogStream;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;

import java.time.Instant;

public record ContainerLogFilter(
        ContainerService service,
        ContainerLogLevel level,
        ContainerLogStream stream,
        Instant from,
        Instant to,
        String search
) {
}
