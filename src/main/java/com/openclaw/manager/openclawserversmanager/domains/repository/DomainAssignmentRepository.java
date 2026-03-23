package com.openclaw.manager.openclawserversmanager.domains.repository;

import com.openclaw.manager.openclawserversmanager.domains.entity.AssignmentStatus;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DomainAssignmentRepository extends JpaRepository<DomainAssignment, UUID> {

    List<DomainAssignment> findByZoneId(UUID zoneId);

    List<DomainAssignment> findByResourceId(UUID resourceId);

    Optional<DomainAssignment> findByHostnameAndStatusNot(String hostname, AssignmentStatus status);

    List<DomainAssignment> findByStatus(AssignmentStatus status);

    List<DomainAssignment> findByResourceIdAndStatusNot(UUID resourceId, AssignmentStatus status);

    boolean existsByZoneIdAndStatusNot(UUID zoneId, AssignmentStatus status);
}
