package com.openclaw.manager.openclawserversmanager.deployment.dto;

import com.openclaw.manager.openclawserversmanager.deployment.entity.ScriptType;

import java.time.Instant;
import java.util.UUID;

public record ScriptResponse(
        UUID id,
        String name,
        String description,
        String scriptContent,
        ScriptType scriptType,
        Instant createdAt,
        Instant updatedAt
) {}
