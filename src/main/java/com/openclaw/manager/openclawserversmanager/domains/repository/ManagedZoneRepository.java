package com.openclaw.manager.openclawserversmanager.domains.repository;

import com.openclaw.manager.openclawserversmanager.domains.entity.ManagedZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManagedZoneRepository extends JpaRepository<ManagedZone, UUID> {

    List<ManagedZone> findByProviderAccountId(UUID providerAccountId);

    Optional<ManagedZone> findByZoneName(String zoneName);

    Optional<ManagedZone> findByZoneNameAndProviderAccountId(String zoneName, UUID providerAccountId);

    List<ManagedZone> findByActive(boolean active);

    boolean existsByProviderAccountId(UUID providerAccountId);

    Optional<ManagedZone> findByDefaultForAutoAssignTrue();

    @Modifying
    @Query("UPDATE ManagedZone z SET z.defaultForAutoAssign = false WHERE z.defaultForAutoAssign = true")
    void clearDefaultAutoAssign();
}
