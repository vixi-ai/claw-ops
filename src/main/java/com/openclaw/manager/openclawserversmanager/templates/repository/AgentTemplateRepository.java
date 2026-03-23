package com.openclaw.manager.openclawserversmanager.templates.repository;

import com.openclaw.manager.openclawserversmanager.templates.entity.AgentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AgentTemplateRepository extends JpaRepository<AgentTemplate, UUID> {
    boolean existsByName(String name);
}
