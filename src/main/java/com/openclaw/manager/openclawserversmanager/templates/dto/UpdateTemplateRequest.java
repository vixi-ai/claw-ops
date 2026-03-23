package com.openclaw.manager.openclawserversmanager.templates.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateTemplateRequest(
        @Size(max = 100) String name,
        String description,
        @Pattern(regexp = "[a-z0-9-]+", message = "must be lowercase alphanumeric with hyphens") String agentType,
        String installScript
) {}
