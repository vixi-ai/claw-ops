package com.openclaw.manager.openclawserversmanager.templates.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTemplateRequest(
        @NotBlank @Size(max = 100) String name,
        String description,
        @NotBlank @Pattern(regexp = "[a-z0-9-]+", message = "must be lowercase alphanumeric with hyphens") String agentType,
        @NotBlank String installScript
) {}
