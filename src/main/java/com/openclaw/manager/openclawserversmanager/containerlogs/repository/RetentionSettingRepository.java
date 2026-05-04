package com.openclaw.manager.openclawserversmanager.containerlogs.repository;

import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.RetentionSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetentionSettingRepository extends JpaRepository<RetentionSetting, ContainerService> {
}
