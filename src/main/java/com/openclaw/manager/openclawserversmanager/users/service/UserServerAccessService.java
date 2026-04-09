package com.openclaw.manager.openclawserversmanager.users.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.repository.ServerRepository;
import com.openclaw.manager.openclawserversmanager.users.dto.ServerAccessResponse;
import com.openclaw.manager.openclawserversmanager.users.entity.Role;
import com.openclaw.manager.openclawserversmanager.users.entity.User;
import com.openclaw.manager.openclawserversmanager.users.entity.UserServerAccess;
import com.openclaw.manager.openclawserversmanager.users.repository.UserRepository;
import com.openclaw.manager.openclawserversmanager.users.repository.UserServerAccessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserServerAccessService {

    private final UserServerAccessRepository accessRepository;
    private final UserRepository userRepository;
    private final ServerRepository serverRepository;
    private final AuditService auditService;

    public UserServerAccessService(UserServerAccessRepository accessRepository,
                                   UserRepository userRepository,
                                   ServerRepository serverRepository,
                                   AuditService auditService) {
        this.accessRepository = accessRepository;
        this.userRepository = userRepository;
        this.serverRepository = serverRepository;
        this.auditService = auditService;
    }

    @Transactional
    public List<ServerAccessResponse> assignServers(UUID userId, List<UUID> serverIds, UUID assignedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getRole() != Role.EMPLOYEE) {
            throw new IllegalStateException("Server access can only be assigned to EMPLOYEE users");
        }

        List<ServerAccessResponse> results = new ArrayList<>();
        for (UUID serverId : serverIds) {
            if (accessRepository.existsByUserIdAndServerId(userId, serverId)) {
                continue; // skip duplicates
            }

            Server server = serverRepository.findById(serverId)
                    .orElseThrow(() -> new ResourceNotFoundException("Server not found: " + serverId));

            UserServerAccess access = new UserServerAccess();
            access.setUserId(userId);
            access.setServerId(serverId);
            access.setAssignedBy(assignedBy);
            UserServerAccess saved = accessRepository.save(access);

            results.add(toResponse(saved, server.getName()));

            try {
                auditService.log(AuditAction.SERVER_ACCESS_GRANTED, "USER_SERVER_ACCESS",
                        saved.getId(), assignedBy,
                        "Server '%s' access granted to user %s".formatted(server.getName(), user.getUsername()));
            } catch (Exception ignored) {}
        }

        return results;
    }

    @Transactional
    public void revokeServer(UUID userId, UUID serverId, UUID currentUserId) {
        if (!accessRepository.existsByUserIdAndServerId(userId, serverId)) {
            throw new ResourceNotFoundException("Access not found for user %s and server %s".formatted(userId, serverId));
        }

        accessRepository.deleteByUserIdAndServerId(userId, serverId);

        try {
            auditService.log(AuditAction.SERVER_ACCESS_REVOKED, "USER_SERVER_ACCESS",
                    null, currentUserId,
                    "Server %s access revoked from user %s".formatted(serverId, userId));
        } catch (Exception ignored) {}
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        accessRepository.deleteByUserId(userId);
    }

    public List<UUID> getAccessibleServerIds(UUID userId) {
        return accessRepository.findServerIdsByUserId(userId);
    }

    public boolean hasAccessToServer(UUID userId, UUID serverId) {
        return accessRepository.existsByUserIdAndServerId(userId, serverId);
    }

    public List<ServerAccessResponse> getServerAccessForUser(UUID userId) {
        List<UserServerAccess> accessList = accessRepository.findByUserId(userId);
        if (accessList.isEmpty()) return List.of();

        List<UUID> serverIds = accessList.stream().map(UserServerAccess::getServerId).toList();
        Map<UUID, String> serverNames = serverRepository.findAllById(serverIds).stream()
                .collect(Collectors.toMap(Server::getId, Server::getName));

        return accessList.stream()
                .map(a -> toResponse(a, serverNames.getOrDefault(a.getServerId(), "Unknown")))
                .toList();
    }

    private ServerAccessResponse toResponse(UserServerAccess access, String serverName) {
        return new ServerAccessResponse(
                access.getId(),
                access.getUserId(),
                access.getServerId(),
                serverName,
                access.getAssignedAt(),
                access.getAssignedBy()
        );
    }
}
