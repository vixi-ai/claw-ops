package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.NotificationChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateChannelRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull NotificationChannelType channelType,
        @NotBlank String config
) {}
