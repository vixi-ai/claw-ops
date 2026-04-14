package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.IncidentSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateIncidentRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        @NotNull UUID serverId,
        @NotNull IncidentSeverity severity
) {}
