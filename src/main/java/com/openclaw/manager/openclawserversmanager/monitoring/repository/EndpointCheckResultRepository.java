package com.openclaw.manager.openclawserversmanager.monitoring.repository;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.EndpointCheckResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EndpointCheckResultRepository extends JpaRepository<EndpointCheckResult, UUID> {

    Optional<EndpointCheckResult> findFirstByEndpointCheckIdOrderByCheckedAtDesc(UUID endpointCheckId);

    List<EndpointCheckResult> findByEndpointCheckIdOrderByCheckedAtDesc(UUID endpointCheckId);

    Page<EndpointCheckResult> findByEndpointCheckIdOrderByCheckedAtDesc(UUID endpointCheckId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM EndpointCheckResult r WHERE r.checkedAt < :before")
    long deleteByCheckedAtBefore(Instant before);
}
