package com.openclaw.manager.openclawserversmanager.monitoring.repository;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.Incident;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    Page<Incident> findByServerIdOrderByOpenedAtDesc(UUID serverId, Pageable pageable);

    List<Incident> findByStatus(IncidentStatus status);

    long countByStatusIn(List<IncidentStatus> statuses);
}
