package com.openclaw.manager.openclawserversmanager.notifications.repository;

import com.openclaw.manager.openclawserversmanager.notifications.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {

    List<UserDevice> findByUserId(UUID userId);

    List<UserDevice> findByUserIdAndNotificationsEnabledTrue(UUID userId);

    void deleteByIdAndUserId(UUID id, UUID userId);
}
