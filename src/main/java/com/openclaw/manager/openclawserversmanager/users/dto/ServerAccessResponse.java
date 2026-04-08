package com.openclaw.manager.openclawserversmanager.users.dto;

import java.time.Instant;
import java.util.UUID;

public record ServerAccessResponse(
        UUID id,
        UUID userId,
        UUID serverId,
        String serverName,
        Instant assignedAt,
        UUID assignedBy
) {}
