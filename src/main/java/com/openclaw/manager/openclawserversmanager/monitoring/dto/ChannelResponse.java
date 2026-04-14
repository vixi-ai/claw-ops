package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.NotificationChannel;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.NotificationChannelType;

import java.time.Instant;
import java.util.UUID;

public record ChannelResponse(
        UUID id,
        String name,
        NotificationChannelType channelType,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static ChannelResponse from(NotificationChannel c) {
        return new ChannelResponse(
                c.getId(), c.getName(), c.getChannelType(),
                c.isEnabled(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
