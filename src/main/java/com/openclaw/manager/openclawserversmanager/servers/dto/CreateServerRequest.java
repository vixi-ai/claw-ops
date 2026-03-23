package com.openclaw.manager.openclawserversmanager.servers.dto;

import com.openclaw.manager.openclawserversmanager.servers.entity.AuthType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record CreateServerRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank String hostname,
        String ipAddress,
        @Min(1) @Max(65535) Integer sshPort,
        @NotBlank String sshUsername,
        @NotNull AuthType authType,
        UUID credentialId,
        UUID passphraseCredentialId,
        String environment,
        String rootDomain,
        String subdomain,
        UUID zoneId,
        Map<String, Object> metadata
) {
    public CreateServerRequest {
        if (sshPort == null) sshPort = 22;
        if (environment == null || environment.isBlank()) environment = "production";
    }
}
