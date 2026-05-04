package com.openclaw.manager.openclawserversmanager.containerlogs.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.containerlogs.dto.ContainerLogFilter;
import com.openclaw.manager.openclawserversmanager.containerlogs.dto.ContainerLogResponse;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import com.openclaw.manager.openclawserversmanager.containerlogs.repository.ContainerLogRepository;
import com.openclaw.manager.openclawserversmanager.containerlogs.specification.ContainerLogSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ContainerLogQueryService {

    private final ContainerLogRepository repository;
    private final AuditService auditService;

    public ContainerLogQueryService(ContainerLogRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public Page<ContainerLogResponse> query(ContainerLogFilter filter, Pageable pageable) {
        return repository.findAll(ContainerLogSpecification.withFilter(filter), pageable)
                .map(ContainerLogResponse::from);
    }

    @Transactional
    public long deleteOldLogs(Instant before, ContainerService service, UUID userId) {
        long deleted = (service == null)
                ? repository.deleteByLogTsBefore(before)
                : repository.deleteByServiceAndLogTsBefore(service, before);
        auditService.log(AuditAction.CONTAINER_LOGS_DELETED, "CONTAINER_LOG", null, userId,
                "before=" + before + " service=" + service + " deleted=" + deleted);
        return deleted;
    }
}
