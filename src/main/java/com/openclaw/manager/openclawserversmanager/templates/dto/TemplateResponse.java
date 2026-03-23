package com.openclaw.manager.openclawserversmanager.templates.dto;

import java.time.Instant;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        String name,
        String description,
        String agentType,
        String installScript,
        Instant createdAt,
        Instant updatedAt
) {}
