package com.openclaw.manager.openclawserversmanager.secrets.dto;

import com.openclaw.manager.openclawserversmanager.secrets.entity.SecretType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSecretRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull SecretType type,
        @NotBlank String value,
        String description
) {
}
