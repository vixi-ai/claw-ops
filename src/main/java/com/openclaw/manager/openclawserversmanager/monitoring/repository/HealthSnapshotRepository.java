package com.openclaw.manager.openclawserversmanager.monitoring.repository;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.HealthSnapshot;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.HealthState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HealthSnapshotRepository extends JpaRepository<HealthSnapshot, UUID> {

    Optional<HealthSnapshot> findByServerId(UUID serverId);

    long countByOverallState(HealthState state);
}
