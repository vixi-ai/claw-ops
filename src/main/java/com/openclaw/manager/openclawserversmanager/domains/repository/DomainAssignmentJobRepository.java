package com.openclaw.manager.openclawserversmanager.domains.repository;

import com.openclaw.manager.openclawserversmanager.domains.entity.DomainAssignmentJob;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainJobStatus;
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

public interface DomainAssignmentJobRepository extends JpaRepository<DomainAssignmentJob, UUID> {

    List<DomainAssignmentJob> findByDomainAssignmentId(UUID assignmentId);

    List<DomainAssignmentJob> findByServerId(UUID serverId);

    List<DomainAssignmentJob> findByStatus(DomainJobStatus status);

    List<DomainAssignmentJob> findByStatusIn(List<DomainJobStatus> statuses);

    boolean existsByDomainAssignmentIdAndStatus(UUID assignmentId, DomainJobStatus status);

    Optional<DomainAssignmentJob> findFirstByDomainAssignmentIdOrderByCreatedAtDesc(UUID assignmentId);

    Optional<DomainAssignmentJob> findFirstByServerIdOrderByCreatedAtDesc(UUID serverId);

    Page<DomainAssignmentJob> findByServerId(UUID serverId, Pageable pageable);

    Page<DomainAssignmentJob> findByServerIdAndStatus(UUID serverId, DomainJobStatus status, Pageable pageable);

    /**
     * Force-fail a job row via direct UPDATE. Last-resort when the runner can't load the
     * entity after retries.
     */
    @Modifying
    @Transactional
    @Query("UPDATE DomainAssignmentJob j SET j.status = com.openclaw.manager.openclawserversmanager.domains.entity.DomainJobStatus.FAILED, " +
            "j.currentStep = com.openclaw.manager.openclawserversmanager.domains.entity.DomainJobStep.FAILED_PERMANENT, " +
            "j.errorMessage = :msg, j.finishedAt = CURRENT_TIMESTAMP WHERE j.id = :id")
    int markFailedById(@Param("id") UUID id, @Param("msg") String msg);
}
