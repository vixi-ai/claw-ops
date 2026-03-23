package com.openclaw.manager.openclawserversmanager.deployment.repository;

import com.openclaw.manager.openclawserversmanager.deployment.entity.DeploymentJob;
import com.openclaw.manager.openclawserversmanager.deployment.entity.DeploymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeploymentJobRepository extends JpaRepository<DeploymentJob, UUID> {
    boolean existsByServerIdAndStatus(UUID serverId, DeploymentStatus status);
    Page<DeploymentJob> findByServerId(UUID serverId, Pageable pageable);
    Page<DeploymentJob> findByStatus(DeploymentStatus status, Pageable pageable);
    Page<DeploymentJob> findByServerIdAndStatus(UUID serverId, DeploymentStatus status, Pageable pageable);
}
