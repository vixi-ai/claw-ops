package com.openclaw.manager.openclawserversmanager.terminal.model;

import java.time.Instant;
import java.util.UUID;

public record SessionTokenInfo(
        String token,
        UUID userId,
        UUID serverId,
        Instant expiresAt,
        UUID jobId,
        String existingSessionId
) {
    public SessionTokenInfo(String token, UUID userId, UUID serverId, Instant expiresAt) {
        this(token, userId, serverId, expiresAt, null, null);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isDeploymentToken() {
        return jobId != null;
    }

    public boolean isReconnectionToken() {
        return existingSessionId != null;
    }
}
