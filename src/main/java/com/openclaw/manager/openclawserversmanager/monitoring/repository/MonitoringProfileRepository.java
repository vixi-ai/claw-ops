package com.openclaw.manager.openclawserversmanager.monitoring.repository;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.MonitoringProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MonitoringProfileRepository extends JpaRepository<MonitoringProfile, UUID> {

    Optional<MonitoringProfile> findByServerId(UUID serverId);

    boolean existsByServerId(UUID serverId);
}
