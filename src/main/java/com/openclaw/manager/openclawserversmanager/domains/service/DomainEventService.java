package com.openclaw.manager.openclawserversmanager.domains.service;

import com.openclaw.manager.openclawserversmanager.domains.dto.DomainEventResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainEvent;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainEventAction;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainEventOutcome;
import com.openclaw.manager.openclawserversmanager.domains.mapper.DomainEventMapper;
import com.openclaw.manager.openclawserversmanager.domains.repository.DomainEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DomainEventService {

    private static final Logger log = LoggerFactory.getLogger(DomainEventService.class);

    private final DomainEventRepository domainEventRepository;

    public DomainEventService(DomainEventRepository domainEventRepository) {
        this.domainEventRepository = domainEventRepository;
    }

    @Transactional
    public void recordEvent(UUID assignmentId, UUID zoneId, DomainEventAction action,
                            DomainEventOutcome outcome, String correlationId, String details) {
        try {
            DomainEvent event = new DomainEvent();
            event.setAssignmentId(assignmentId);
            event.setZoneId(zoneId);
            event.setAction(action);
            event.setOutcome(outcome);
            event.setProviderCorrelationId(correlationId);
            event.setDetails(details);
            domainEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to record domain event: {}", e.getMessage());
        }
    }

    public List<DomainEventResponse> getEventsForAssignment(UUID assignmentId) {
        return domainEventRepository.findByAssignmentIdOrderByCreatedAtDesc(assignmentId).stream()
                .map(DomainEventMapper::toResponse)
                .toList();
    }

    public List<DomainEventResponse> getEventsForZone(UUID zoneId) {
        return domainEventRepository.findByZoneIdOrderByCreatedAtDesc(zoneId).stream()
                .map(DomainEventMapper::toResponse)
                .toList();
    }
}
