package com.openclaw.manager.openclawserversmanager.deployment.dto;

public record TerminalTokenResponse(
        String token,
        String sessionId
) {}
