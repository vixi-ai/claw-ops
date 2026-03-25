package com.openclaw.manager.openclawserversmanager.domains.repository;

import com.openclaw.manager.openclawserversmanager.domains.entity.ProvisioningJob;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProvisioningJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProvisioningJobRepository extends JpaRepository<ProvisioningJob, UUID> {

    List<ProvisioningJob> findByDomainAssignmentId(UUID assignmentId);

    List<ProvisioningJob> findByServerId(UUID serverId);

    List<ProvisioningJob> findByStatus(ProvisioningJobStatus status);

    boolean existsByDomainAssignmentIdAndStatus(UUID assignmentId, ProvisioningJobStatus status);

    Optional<ProvisioningJob> findFirstByDomainAssignmentIdOrderByCreatedAtDesc(UUID assignmentId);

    Page<ProvisioningJob> findByServerId(UUID serverId, Pageable pageable);

    Page<ProvisioningJob> findByServerIdAndStatus(UUID serverId, ProvisioningJobStatus status, Pageable pageable);
}
