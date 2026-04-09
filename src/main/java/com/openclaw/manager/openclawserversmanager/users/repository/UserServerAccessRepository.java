package com.openclaw.manager.openclawserversmanager.users.repository;

import com.openclaw.manager.openclawserversmanager.users.entity.UserServerAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserServerAccessRepository extends JpaRepository<UserServerAccess, UUID> {

    List<UserServerAccess> findByUserId(UUID userId);

    boolean existsByUserIdAndServerId(UUID userId, UUID serverId);

    void deleteByUserIdAndServerId(UUID userId, UUID serverId);

    void deleteByUserId(UUID userId);

    @Query("SELECT usa.serverId FROM UserServerAccess usa WHERE usa.userId = :userId")
    List<UUID> findServerIdsByUserId(@Param("userId") UUID userId);
}
