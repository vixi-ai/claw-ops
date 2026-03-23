package com.openclaw.manager.openclawserversmanager.secrets.dto;

import jakarta.validation.constraints.Size;

public record UpdateSecretRequest(
        @Size(max = 100) String name,
        String value,
        String description
) {
}
