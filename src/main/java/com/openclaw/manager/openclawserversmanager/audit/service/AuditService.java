package com.openclaw.manager.openclawserversmanager.audit.service;

import com.openclaw.manager.openclawserversmanager.audit.dto.AuditLogFilter;
import com.openclaw.manager.openclawserversmanager.audit.dto.AuditLogResponse;
import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.entity.AuditLog;
import com.openclaw.manager.openclawserversmanager.audit.repository.AuditLogRepository;
import com.openclaw.manager.openclawserversmanager.audit.specification.AuditLogSpecification;
import com.openclaw.manager.openclawserversmanager.audit.util.AuditContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(AuditAction action, String entityType, UUID entityId, UUID userId, String details) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setUserId(userId);
            entry.setDetails(details);
            entry.setIpAddress(AuditContext.getCurrentIpAddress());
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to write audit log: action={}, entityType={}, entityId={}", action, entityType, entityId, e);
        }
    }

    public void log(AuditAction action, String entityType, UUID entityId, UUID userId) {
        log(action, entityType, entityId, userId, null);
    }

    public Page<AuditLogResponse> getLogs(AuditLogFilter filter, Pageable pageable) {
        return auditLogRepository.findAll(AuditLogSpecification.withFilter(filter), pageable)
                .map(this::toResponse);
    }

    public Page<AuditLogResponse> getLogsForEntity(String entityType, UUID entityId, Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public long deleteOldAuditLogs(Instant before) {
        long deleted = auditLogRepository.deleteByCreatedAtBefore(before);
        log.info("Deleted {} audit log entries older than {}", deleted, before);
        return deleted;
    }

    private AuditLogResponse toResponse(AuditLog entry) {
        return new AuditLogResponse(
                entry.getId(),
                entry.getUserId(),
                entry.getAction(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getDetails(),
                entry.getIpAddress(),
                entry.getCreatedAt()
        );
    }
}
