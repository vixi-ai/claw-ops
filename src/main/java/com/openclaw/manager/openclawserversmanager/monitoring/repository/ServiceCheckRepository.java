package com.openclaw.manager.openclawserversmanager.monitoring.repository;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.ServiceCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceCheckRepository extends JpaRepository<ServiceCheck, UUID> {

    List<ServiceCheck> findByServerIdOrderByCheckedAtDesc(UUID serverId);

    List<ServiceCheck> findByServerIdAndServiceNameOrderByCheckedAtDesc(UUID serverId, String serviceName);
}
