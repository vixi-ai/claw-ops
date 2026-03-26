package com.openclaw.manager.openclawserversmanager.monitoring.repository;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.MaintenanceWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindow, UUID> {

    List<MaintenanceWindow> findByServerIdAndStartAtBeforeAndEndAtAfter(UUID serverId, Instant now1, Instant now2);

    List<MaintenanceWindow> findByServerId(UUID serverId);

    List<MaintenanceWindow> findByEndAtBefore(Instant now);

    List<MaintenanceWindow> findByStartAtBeforeAndEndAtAfter(Instant now1, Instant now2);
}
