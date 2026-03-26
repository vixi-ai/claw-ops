package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import java.time.Instant;
import java.util.UUID;

public record MaintenanceWindowResponse(
    UUID id,
    UUID serverId,
    String serverName,
    String reason,
    Instant startAt,
    Instant endAt,
    UUID createdBy,
    Instant createdAt
) {}
