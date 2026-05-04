package com.openclaw.manager.openclawserversmanager.containerlogs.dto;

import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;

import java.time.Instant;

public record DeleteContainerLogsResponse(
        long deletedCount,
        Instant before,
        ContainerService service
) {
}
