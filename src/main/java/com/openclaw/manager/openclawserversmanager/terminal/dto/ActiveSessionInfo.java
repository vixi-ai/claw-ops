package com.openclaw.manager.openclawserversmanager.terminal.dto;

import java.time.Instant;
import java.util.UUID;

public record ActiveSessionInfo(
        String sessionId,
        UUID serverId,
        String serverName,
        UUID userId,
        String type,
        String status,
        Instant createdAt,
        Instant lastActivityAt,
        long durationSeconds,
        boolean sshConnected,
        boolean hasWebSocket,
        UUID deploymentJobId,
        int bufferSize
) {}
