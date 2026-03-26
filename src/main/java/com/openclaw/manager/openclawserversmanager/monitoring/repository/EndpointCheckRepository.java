package com.openclaw.manager.openclawserversmanager.monitoring.repository;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.EndpointCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EndpointCheckRepository extends JpaRepository<EndpointCheck, UUID> {

    List<EndpointCheck> findByEnabled(boolean enabled);

    List<EndpointCheck> findByServerId(UUID serverId);
}
