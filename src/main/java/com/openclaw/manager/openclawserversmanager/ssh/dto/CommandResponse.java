package com.openclaw.manager.openclawserversmanager.ssh.dto;

import java.util.UUID;

public record CommandResponse(
        int exitCode,
        String stdout,
        String stderr,
        long durationMs,
        UUID serverId,
        String serverName
) {
}
