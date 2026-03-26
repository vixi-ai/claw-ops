package com.openclaw.manager.openclawserversmanager.monitoring.mapper;

import com.openclaw.manager.openclawserversmanager.monitoring.dto.MonitoringProfileResponse;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.MonitoringProfile;

public class MonitoringProfileMapper {

    private MonitoringProfileMapper() {}

    public static MonitoringProfileResponse toResponse(MonitoringProfile profile) {
        return new MonitoringProfileResponse(
                profile.getId(),
                profile.getServerId(),
                profile.isEnabled(),
                profile.getCheckIntervalSeconds(),
                profile.getMetricRetentionDays(),
                profile.getCpuWarningThreshold(),
                profile.getCpuCriticalThreshold(),
                profile.getMemoryWarningThreshold(),
                profile.getMemoryCriticalThreshold(),
                profile.getDiskWarningThreshold(),
                profile.getDiskCriticalThreshold(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
