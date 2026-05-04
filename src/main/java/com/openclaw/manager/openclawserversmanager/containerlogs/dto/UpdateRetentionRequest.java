package com.openclaw.manager.openclawserversmanager.containerlogs.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateRetentionRequest(
        @NotNull
        @Min(1)
        @Max(3650)
        Integer retentionDays
) {
}
