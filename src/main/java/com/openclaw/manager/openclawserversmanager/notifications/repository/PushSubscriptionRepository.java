package com.openclaw.manager.openclawserversmanager.notifications.repository;

import com.openclaw.manager.openclawserversmanager.notifications.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByProviderId(UUID providerId);

    void deleteByEndpoint(String endpoint);

    List<PushSubscription> findByDeviceIdIn(List<UUID> deviceIds);

    Optional<PushSubscription> findByDeviceId(UUID deviceId);
}
