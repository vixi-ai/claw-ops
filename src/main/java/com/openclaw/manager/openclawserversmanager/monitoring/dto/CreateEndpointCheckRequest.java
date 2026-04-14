package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record CreateEndpointCheckRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 2000) String url,
        @NotBlank String checkType,
        UUID serverId,
        Integer expectedStatusCode,
        @Min(30) @Max(86400) int intervalSeconds
) {
    public CreateEndpointCheckRequest {
        if (intervalSeconds == 0) intervalSeconds = 300;
    }
}
