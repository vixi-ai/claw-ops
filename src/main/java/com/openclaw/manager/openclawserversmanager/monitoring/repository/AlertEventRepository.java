package com.openclaw.manager.openclawserversmanager.monitoring.repository;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.AlertEvent;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertEventRepository extends JpaRepository<AlertEvent, UUID> {

    Page<AlertEvent> findByServerIdOrderByFiredAtDesc(UUID serverId, Pageable pageable);

    List<AlertEvent> findByStatus(AlertStatus status);

    long countByStatus(AlertStatus status);

    List<AlertEvent> findByIncidentId(UUID incidentId);

    List<AlertEvent> findByAlertRuleIdAndStatus(UUID alertRuleId, AlertStatus status);
}
