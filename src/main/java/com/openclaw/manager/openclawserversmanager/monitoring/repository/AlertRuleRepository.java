package com.openclaw.manager.openclawserversmanager.monitoring.repository;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {

    Optional<AlertRule> findByName(String name);

    boolean existsByName(String name);

    List<AlertRule> findByEnabled(boolean enabled);

    List<AlertRule> findByServerIdOrServerIdIsNull(UUID serverId);

    List<AlertRule> findByServerId(UUID serverId);
}
