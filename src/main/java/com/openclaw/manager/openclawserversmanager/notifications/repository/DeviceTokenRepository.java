package com.openclaw.manager.openclawserversmanager.notifications.repository;

import com.openclaw.manager.openclawserversmanager.notifications.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByTokenAndProviderId(String token, UUID providerId);

    List<DeviceToken> findByProviderId(UUID providerId);

    void deleteByTokenAndProviderId(String token, UUID providerId);

    void deleteByProviderId(UUID providerId);

    List<DeviceToken> findByDeviceIdIn(List<UUID> deviceIds);

    Optional<DeviceToken> findByDeviceId(UUID deviceId);

    List<DeviceToken> findByUserId(UUID userId);
}
