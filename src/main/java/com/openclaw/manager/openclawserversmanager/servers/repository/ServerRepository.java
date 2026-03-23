package com.openclaw.manager.openclawserversmanager.servers.repository;

import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.entity.ServerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServerRepository extends JpaRepository<Server, UUID> {

    Optional<Server> findByName(String name);

    boolean existsByName(String name);

    Page<Server> findByEnvironment(String environment, Pageable pageable);

    List<Server> findByStatus(ServerStatus status);

    boolean existsByCredentialId(UUID credentialId);

    List<Server> findBySubdomainIsNotNull();
}
