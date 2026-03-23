package com.openclaw.manager.openclawserversmanager.deployment.dto;

import com.openclaw.manager.openclawserversmanager.deployment.entity.ScriptType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateScriptRequest(
        @NotBlank @Size(max = 100) String name,
        String description,
        @NotBlank String scriptContent,
        @NotNull ScriptType scriptType
) {}
