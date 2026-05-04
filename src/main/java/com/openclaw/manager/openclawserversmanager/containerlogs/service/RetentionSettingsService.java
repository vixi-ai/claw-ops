package com.openclaw.manager.openclawserversmanager.containerlogs.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.containerlogs.dto.RetentionSettingDto;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.RetentionSetting;
import com.openclaw.manager.openclawserversmanager.containerlogs.repository.RetentionSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RetentionSettingsService {

    private static final int DEFAULT_DAYS = 7;

    private final RetentionSettingRepository repository;
    private final AuditService auditService;
    private final Map<ContainerService, Integer> cache = new ConcurrentHashMap<>();

    public RetentionSettingsService(RetentionSettingRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public List<RetentionSettingDto> getAll() {
        Map<ContainerService, RetentionSetting> existing = new EnumMap<>(ContainerService.class);
        for (RetentionSetting r : repository.findAll()) existing.put(r.getService(), r);

        List<RetentionSettingDto> out = new ArrayList<>();
        for (ContainerService svc : ContainerService.values()) {
            RetentionSetting r = existing.get(svc);
            if (r == null) {
                r = repository.save(new RetentionSetting(svc, DEFAULT_DAYS));
            }
            cache.put(svc, r.getRetentionDays());
            out.add(RetentionSettingDto.from(r));
        }
        return out;
    }

    @Transactional
    public RetentionSettingDto update(ContainerService service, int retentionDays, UUID userId) {
        if (retentionDays < 1 || retentionDays > 3650) {
            throw new IllegalArgumentException("retentionDays must be between 1 and 3650");
        }
        RetentionSetting r = repository.findById(service)
                .orElseGet(() -> new RetentionSetting(service, DEFAULT_DAYS));
        r.setService(service);
        r.setRetentionDays(retentionDays);
        r.setUpdatedAt(Instant.now());
        r.setUpdatedByUserId(userId);
        RetentionSetting saved = repository.save(r);
        cache.put(service, retentionDays);

        auditService.log(AuditAction.CONTAINER_LOG_RETENTION_UPDATED, "CONTAINER_LOG_SETTINGS", null, userId,
                "service=" + service + " days=" + retentionDays);

        return RetentionSettingDto.from(saved);
    }

    public int getDaysFor(ContainerService service) {
        Integer v = cache.get(service);
        if (v != null) return v;
        return repository.findById(service)
                .map(RetentionSetting::getRetentionDays)
                .orElse(DEFAULT_DAYS);
    }
}
