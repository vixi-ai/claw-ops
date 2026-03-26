package com.openclaw.manager.openclawserversmanager.monitoring.repository;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.NotificationChannel;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.NotificationChannelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, UUID> {

    Optional<NotificationChannel> findByName(String name);

    boolean existsByName(String name);

    List<NotificationChannel> findByEnabled(boolean enabled);

    List<NotificationChannel> findByChannelType(NotificationChannelType channelType);
}
