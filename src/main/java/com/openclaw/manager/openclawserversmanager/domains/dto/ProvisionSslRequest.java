package com.openclaw.manager.openclawserversmanager.domains.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProvisionSslRequest(
        @NotNull(message = "serverId is required")
        UUID serverId,

        @Min(value = 1, message = "targetPort must be at least 1")
        Integer targetPort
) {
}
