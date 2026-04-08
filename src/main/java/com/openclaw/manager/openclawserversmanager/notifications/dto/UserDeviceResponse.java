package com.openclaw.manager.openclawserversmanager.notifications.dto;

import java.time.Instant;
import java.util.UUID;

public record UserDeviceResponse(
        UUID id,
        String deviceName,
        String platform,
        boolean notificationsEnabled,
        Instant createdAt,
        Instant updatedAt
) {}
