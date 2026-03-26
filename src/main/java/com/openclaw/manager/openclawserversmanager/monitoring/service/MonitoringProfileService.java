package com.openclaw.manager.openclawserversmanager.monitoring.service;

import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.monitoring.dto.MonitoringProfileResponse;
import com.openclaw.manager.openclawserversmanager.monitoring.dto.UpdateMonitoringProfileRequest;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.MonitoringProfile;
import com.openclaw.manager.openclawserversmanager.monitoring.mapper.MonitoringProfileMapper;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.MonitoringProfileRepository;
import com.openclaw.manager.openclawserversmanager.servers.repository.ServerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class MonitoringProfileService {

    private final MonitoringProfileRepository monitoringProfileRepository;
    private final ServerRepository serverRepository;

    public MonitoringProfileService(MonitoringProfileRepository monitoringProfileRepository,
                                    ServerRepository serverRepository) {
        this.monitoringProfileRepository = monitoringProfileRepository;
        this.serverRepository = serverRepository;
    }

    @Transactional(readOnly = true)
    public MonitoringProfileResponse getProfile(UUID serverId) {
        if (!serverRepository.existsById(serverId)) {
            throw new ResourceNotFoundException("Server with id " + serverId + " not found");
        }

        MonitoringProfile profile = monitoringProfileRepository.findByServerId(serverId)
                .orElseGet(() -> createDefaultProfile(serverId));

        return MonitoringProfileMapper.toResponse(profile);
    }

    @Transactional
    public MonitoringProfileResponse updateProfile(UUID serverId, UpdateMonitoringProfileRequest request) {
        if (!serverRepository.existsById(serverId)) {
            throw new ResourceNotFoundException("Server with id " + serverId + " not found");
        }

        MonitoringProfile profile = monitoringProfileRepository.findByServerId(serverId)
                .orElseGet(() -> createDefaultProfile(serverId));

        if (request.enabled() != null) profile.setEnabled(request.enabled());
        if (request.checkIntervalSeconds() != null) profile.setCheckIntervalSeconds(request.checkIntervalSeconds());
        if (request.metricRetentionDays() != null) profile.setMetricRetentionDays(request.metricRetentionDays());
        if (request.cpuWarningThreshold() != null) profile.setCpuWarningThreshold(request.cpuWarningThreshold());
        if (request.cpuCriticalThreshold() != null) profile.setCpuCriticalThreshold(request.cpuCriticalThreshold());
        if (request.memoryWarningThreshold() != null) profile.setMemoryWarningThreshold(request.memoryWarningThreshold());
        if (request.memoryCriticalThreshold() != null) profile.setMemoryCriticalThreshold(request.memoryCriticalThreshold());
        if (request.diskWarningThreshold() != null) profile.setDiskWarningThreshold(request.diskWarningThreshold());
        if (request.diskCriticalThreshold() != null) profile.setDiskCriticalThreshold(request.diskCriticalThreshold());

        return MonitoringProfileMapper.toResponse(monitoringProfileRepository.save(profile));
    }

    @Transactional
    public MonitoringProfileResponse resetProfile(UUID serverId) {
        if (!serverRepository.existsById(serverId)) {
            throw new ResourceNotFoundException("Server with id " + serverId + " not found");
        }

        monitoringProfileRepository.findByServerId(serverId)
                .ifPresent(monitoringProfileRepository::delete);

        MonitoringProfile profile = createDefaultProfile(serverId);
        return MonitoringProfileMapper.toResponse(profile);
    }

    private MonitoringProfile createDefaultProfile(UUID serverId) {
        MonitoringProfile profile = new MonitoringProfile();
        profile.setServerId(serverId);
        profile.setEnabled(true);
        profile.setCheckIntervalSeconds(60);
        profile.setMetricRetentionDays(7);
        profile.setCpuWarningThreshold(new BigDecimal("80.00"));
        profile.setCpuCriticalThreshold(new BigDecimal("95.00"));
        profile.setMemoryWarningThreshold(new BigDecimal("80.00"));
        profile.setMemoryCriticalThreshold(new BigDecimal("95.00"));
        profile.setDiskWarningThreshold(new BigDecimal("85.00"));
        profile.setDiskCriticalThreshold(new BigDecimal("95.00"));
        return monitoringProfileRepository.save(profile);
    }
}
