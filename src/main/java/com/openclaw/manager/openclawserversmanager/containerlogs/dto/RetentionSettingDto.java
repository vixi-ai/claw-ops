package com.openclaw.manager.openclawserversmanager.containerlogs.dto;

import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.RetentionSetting;

import java.time.Instant;
import java.util.UUID;

public record RetentionSettingDto(
        ContainerService service,
        int retentionDays,
        Instant updatedAt,
        UUID updatedByUserId
) {
    public static RetentionSettingDto from(RetentionSetting setting) {
        return new RetentionSettingDto(
                setting.getService(),
                setting.getRetentionDays(),
                setting.getUpdatedAt(),
                setting.getUpdatedByUserId()
        );
    }
}
