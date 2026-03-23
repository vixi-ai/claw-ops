package com.openclaw.manager.openclawserversmanager.deployment.repository;

import com.openclaw.manager.openclawserversmanager.deployment.entity.DeploymentScript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeploymentScriptRepository extends JpaRepository<DeploymentScript, UUID> {
    boolean existsByName(String name);
}
