package com.openclaw.manager.openclawserversmanager.servers.dto;

public record TestConnectionResponse(
        boolean success,
        String message,
        Long latencyMs
) {
}
