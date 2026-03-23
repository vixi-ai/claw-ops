package com.openclaw.manager.openclawserversmanager.deployment.dto;

import com.openclaw.manager.openclawserversmanager.deployment.entity.ScriptType;
import jakarta.validation.constraints.Size;

public record UpdateScriptRequest(
        @Size(max = 100) String name,
        String description,
        String scriptContent,
        ScriptType scriptType
) {}
