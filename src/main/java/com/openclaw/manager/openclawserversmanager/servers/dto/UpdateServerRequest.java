package com.openclaw.manager.openclawserversmanager.servers.dto;

import com.openclaw.manager.openclawserversmanager.servers.entity.AuthType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record UpdateServerRequest(
        @Size(max = 100) String name,
        String hostname,
        String ipAddress,
        @Min(1) @Max(65535) Integer sshPort,
        String sshUsername,
        AuthType authType,
        UUID credentialId,
        UUID passphraseCredentialId,
        String environment,
        String rootDomain,
        String subdomain,
        Boolean sslEnabled,
        Map<String, Object> metadata
) {
}
