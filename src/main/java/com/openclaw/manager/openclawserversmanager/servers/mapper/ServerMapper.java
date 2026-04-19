package com.openclaw.manager.openclawserversmanager.servers.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.manager.openclawserversmanager.servers.dto.CreateServerRequest;
import com.openclaw.manager.openclawserversmanager.servers.dto.ServerResponse;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;

import java.util.Map;
import java.util.UUID;

public final class ServerMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private ServerMapper() {
    }

    public static ServerResponse toResponse(Server server) {
        return toResponse(server, null, null);
    }

    public static ServerResponse toResponse(Server server, UUID pendingDomainAssignmentId, UUID pendingDomainJobId) {
        String assignedDomain = null;
        if (server.getRootDomain() != null && !server.getRootDomain().isBlank()) {
            assignedDomain = server.getSubdomain() == null || server.getSubdomain().isBlank()
                    ? server.getRootDomain()
                    : server.getSubdomain() + "." + server.getRootDomain();
        }

        return new ServerResponse(
                server.getId(),
                server.getName(),
                server.getHostname(),
                server.getIpAddress(),
                server.getSshPort(),
                server.getSshUsername(),
                server.getAuthType(),
                server.getCredentialId(),
                server.getPassphraseCredentialId(),
                server.getEnvironment(),
                server.getRootDomain(),
                server.getSubdomain(),
                assignedDomain,
                server.isSslEnabled(),
                server.getStatus(),
                deserializeMetadata(server.getMetadata()),
                pendingDomainAssignmentId,
                pendingDomainJobId,
                server.getCreatedAt(),
                server.getUpdatedAt()
        );
    }

    public static Server toEntity(CreateServerRequest request) {
        Server server = new Server();
        server.setName(request.name());
        server.setHostname(request.hostname());
        server.setIpAddress(request.ipAddress());
        server.setSshPort(request.sshPort());
        server.setSshUsername(request.sshUsername());
        server.setAuthType(request.authType());
        server.setCredentialId(request.credentialId());
        server.setPassphraseCredentialId(request.passphraseCredentialId());
        server.setEnvironment(request.environment());
        server.setRootDomain(request.rootDomain());
        server.setSubdomain(request.subdomain());
        server.setMetadata(serializeMetadata(request.metadata()));
        return server;
    }

    public static String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid metadata JSON", e);
        }
    }

    public static Map<String, Object> deserializeMetadata(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
