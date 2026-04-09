package com.openclaw.manager.openclawserversmanager.notifications.repository;

import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProvider;
import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationProviderRepository extends JpaRepository<NotificationProvider, UUID> {

    boolean existsByDisplayName(String displayName);

    Optional<NotificationProvider> findByIsDefaultTrue();

    Optional<NotificationProvider> findFirstByProviderTypeAndEnabledTrue(NotificationProviderType providerType);

    List<NotificationProvider> findByEnabledTrue();

    @Modifying
    @Query("UPDATE NotificationProvider p SET p.isDefault = false WHERE p.isDefault = true")
    void clearDefault();
}
