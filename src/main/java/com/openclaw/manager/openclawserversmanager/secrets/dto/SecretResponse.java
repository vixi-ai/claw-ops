package com.openclaw.manager.openclawserversmanager.secrets.dto;

import com.openclaw.manager.openclawserversmanager.secrets.entity.SecretType;

import java.time.Instant;
import java.util.UUID;

public record SecretResponse(
        UUID id,
        String name,
        SecretType type,
        String description,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
