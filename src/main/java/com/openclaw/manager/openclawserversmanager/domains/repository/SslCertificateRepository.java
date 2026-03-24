package com.openclaw.manager.openclawserversmanager.domains.repository;

import com.openclaw.manager.openclawserversmanager.domains.entity.SslCertificate;
import com.openclaw.manager.openclawserversmanager.domains.entity.SslStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SslCertificateRepository extends JpaRepository<SslCertificate, UUID> {

    Optional<SslCertificate> findByServerId(UUID serverId);

    Optional<SslCertificate> findByHostname(String hostname);

    List<SslCertificate> findByStatus(SslStatus status);

    List<SslCertificate> findByServerIdIn(List<UUID> serverIds);

    Optional<SslCertificate> findByAssignmentId(UUID assignmentId);

    List<SslCertificate> findByAssignmentIdIn(List<UUID> assignmentIds);

    void deleteByServerId(UUID serverId);
}
