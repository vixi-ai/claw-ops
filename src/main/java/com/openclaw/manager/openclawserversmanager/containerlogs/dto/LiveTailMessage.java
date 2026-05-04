package com.openclaw.manager.openclawserversmanager.containerlogs.dto;

import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLogLevel;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLogStream;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;

import java.time.Instant;

/**
 * Frame sent to a WebSocket client. {@code type} discriminates payload shape:
 * LOG (line), WARNING (slow consumer / lifecycle), PONG.
 */
public record LiveTailMessage(
        String type,
        Long id,
        ContainerService service,
        String containerName,
        ContainerLogStream stream,
        ContainerLogLevel level,
        String message,
        Instant ts
) {
    public static LiveTailMessage log(Long id, ContainerService service, String containerName,
                                      ContainerLogStream stream, ContainerLogLevel level,
                                      String message, Instant ts) {
        return new LiveTailMessage("LOG", id, service, containerName, stream, level, message, ts);
    }

    public static LiveTailMessage warning(String message) {
        return new LiveTailMessage("WARNING", null, null, null, null, null, message, Instant.now());
    }

    public static LiveTailMessage pong() {
        return new LiveTailMessage("PONG", null, null, null, null, null, null, Instant.now());
    }
}
