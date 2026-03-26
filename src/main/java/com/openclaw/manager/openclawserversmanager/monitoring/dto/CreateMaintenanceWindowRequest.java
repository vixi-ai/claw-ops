package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateMaintenanceWindowRequest(
    @NotNull UUID serverId,
    @NotBlank @Size(max = 500) String reason,
    @NotNull Instant startAt,
    @NotNull Instant endAt
) {}
