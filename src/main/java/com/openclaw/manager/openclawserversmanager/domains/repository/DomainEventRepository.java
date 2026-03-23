package com.openclaw.manager.openclawserversmanager.domains.repository;

import com.openclaw.manager.openclawserversmanager.domains.entity.DomainEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DomainEventRepository extends JpaRepository<DomainEvent, UUID> {

    List<DomainEvent> findByAssignmentIdOrderByCreatedAtDesc(UUID assignmentId);

    List<DomainEvent> findByZoneIdOrderByCreatedAtDesc(UUID zoneId);
}
