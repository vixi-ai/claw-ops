package com.openclaw.manager.openclawserversmanager.secrets.repository;

import com.openclaw.manager.openclawserversmanager.secrets.entity.Secret;
import com.openclaw.manager.openclawserversmanager.secrets.entity.SecretType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SecretRepository extends JpaRepository<Secret, UUID> {

    Optional<Secret> findByName(String name);

    List<Secret> findByType(SecretType type);

    boolean existsByName(String name);
}
