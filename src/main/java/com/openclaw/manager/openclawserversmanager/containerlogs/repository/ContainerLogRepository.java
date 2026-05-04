package com.openclaw.manager.openclawserversmanager.containerlogs.repository;

import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLog;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ContainerLogRepository extends JpaRepository<ContainerLog, Long>, JpaSpecificationExecutor<ContainerLog> {

    @Modifying
    @Query("DELETE FROM ContainerLog c WHERE c.service = :service AND c.logTs < :before")
    long deleteByServiceAndLogTsBefore(@Param("service") ContainerService service, @Param("before") Instant before);

    @Modifying
    @Query("DELETE FROM ContainerLog c WHERE c.logTs < :before")
    long deleteByLogTsBefore(@Param("before") Instant before);

    List<ContainerLog> findByServiceOrderByLogTsDesc(ContainerService service, Pageable pageable);
}
