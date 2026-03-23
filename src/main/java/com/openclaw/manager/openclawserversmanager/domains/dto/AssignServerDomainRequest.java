package com.openclaw.manager.openclawserversmanager.domains.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignServerDomainRequest(
        @NotNull UUID serverId,
        @NotNull UUID zoneId,
        String hostnameOverride
) {
}
