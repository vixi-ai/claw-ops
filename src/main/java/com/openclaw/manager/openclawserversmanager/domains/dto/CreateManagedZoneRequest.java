package com.openclaw.manager.openclawserversmanager.domains.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record CreateManagedZoneRequest(
        @NotBlank @Size(max = 255) @Pattern(regexp = "^([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}$", message = "must be a valid domain name") String zoneName,
        @NotNull UUID providerAccountId,
        Integer defaultTtl,
        @Size(max = 50) String environmentTag,
        Map<String, Object> metadata
) {
    public CreateManagedZoneRequest {
        if (defaultTtl == null) defaultTtl = 300;
    }
}
