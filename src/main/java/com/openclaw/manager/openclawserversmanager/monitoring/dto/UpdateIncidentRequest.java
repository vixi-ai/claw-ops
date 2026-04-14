package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.IncidentSeverity;
import jakarta.validation.constraints.Size;

public record UpdateIncidentRequest(
        @Size(max = 200) String title,
        String description,
        IncidentSeverity severity
) {}
