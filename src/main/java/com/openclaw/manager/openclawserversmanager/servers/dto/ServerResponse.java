package com.openclaw.manager.openclawserversmanager.servers.dto;

import com.openclaw.manager.openclawserversmanager.servers.entity.AuthType;
import com.openclaw.manager.openclawserversmanager.servers.entity.ServerStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ServerResponse(
        UUID id,
        String name,
        String hostname,
        String ipAddress,
        int sshPort,
        String sshUsername,
        AuthType authType,
        UUID credentialId,
        UUID passphraseCredentialId,
        String environment,
        String rootDomain,
        String subdomain,
        String assignedDomain,
        boolean sslEnabled,
        ServerStatus status,
        Map<String, Object> metadata,
        UUID pendingDomainAssignmentId,
        UUID pendingDomainJobId,
        Instant createdAt,
        Instant updatedAt
) {
}
