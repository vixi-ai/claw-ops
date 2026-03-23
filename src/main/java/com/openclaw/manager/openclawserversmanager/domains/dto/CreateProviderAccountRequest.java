package com.openclaw.manager.openclawserversmanager.domains.dto;

import com.openclaw.manager.openclawserversmanager.domains.entity.DnsProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

public record CreateProviderAccountRequest(
        @NotNull DnsProviderType providerType,
        @NotBlank @Size(max = 100) String displayName,
        @NotNull UUID credentialId,
        Map<String, Object> providerSettings
) {
}
