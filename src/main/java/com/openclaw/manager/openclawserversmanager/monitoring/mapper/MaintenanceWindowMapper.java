package com.openclaw.manager.openclawserversmanager.monitoring.mapper;

import com.openclaw.manager.openclawserversmanager.monitoring.dto.MaintenanceWindowResponse;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.MaintenanceWindow;

public class MaintenanceWindowMapper {

    private MaintenanceWindowMapper() {}

    public static MaintenanceWindowResponse toResponse(MaintenanceWindow window, String serverName) {
        return new MaintenanceWindowResponse(
                window.getId(),
                window.getServerId(),
                serverName,
                window.getReason(),
                window.getStartAt(),
                window.getEndAt(),
                window.getCreatedBy(),
                window.getCreatedAt()
        );
    }
}
