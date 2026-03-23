package com.openclaw.manager.openclawserversmanager.terminal.model;

import java.time.Instant;
import java.util.UUID;

public record SessionTokenInfo(
        String token,
        UUID userId,
        UUID serverId,
        Instant expiresAt
) {
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
