package com.openclaw.manager.openclawserversmanager.domains.repository;

import com.openclaw.manager.openclawserversmanager.domains.entity.ProvisioningJob;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProvisioningJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Force-fail a job row via direct UPDATE. Used by the runner as a last-resort when the
     * entity cannot be loaded after retries — prevents phantom RUNNING rows from lingering
     * forever when the tx-visibility race fires.
     */
    @Modifying
    @Transactional
    @Query("UPDATE ProvisioningJob j SET j.status = com.openclaw.manager.openclawserversmanager.domains.entity.ProvisioningJobStatus.FAILED, " +
            "j.currentStep = com.openclaw.manager.openclawserversmanager.domains.entity.ProvisioningStep.FAILED_PERMANENT, " +
            "j.errorMessage = :msg, j.finishedAt = CURRENT_TIMESTAMP WHERE j.id = :id")
    int markFailedById(@Param("id") UUID id, @Param("msg") String msg);
}
