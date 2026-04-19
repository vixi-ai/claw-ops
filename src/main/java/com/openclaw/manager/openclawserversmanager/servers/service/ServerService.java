package com.openclaw.manager.openclawserversmanager.servers.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.DuplicateResourceException;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.secrets.dto.SecretResponse;
import com.openclaw.manager.openclawserversmanager.secrets.entity.SecretType;
import com.openclaw.manager.openclawserversmanager.secrets.service.SecretService;
import com.openclaw.manager.openclawserversmanager.servers.dto.CreateServerRequest;
import com.openclaw.manager.openclawserversmanager.servers.dto.ServerResponse;
import com.openclaw.manager.openclawserversmanager.servers.dto.TestConnectionResponse;
import com.openclaw.manager.openclawserversmanager.servers.dto.UpdateServerRequest;
import com.openclaw.manager.openclawserversmanager.servers.entity.AuthType;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.entity.ServerStatus;
import com.openclaw.manager.openclawserversmanager.servers.mapper.ServerMapper;
import com.openclaw.manager.openclawserversmanager.servers.repository.ServerRepository;
import com.openclaw.manager.openclawserversmanager.ssh.dto.CommandResponse;
import com.openclaw.manager.openclawserversmanager.ssh.model.CommandResult;
import com.openclaw.manager.openclawserversmanager.ssh.model.TestConnectionResult;
import com.openclaw.manager.openclawserversmanager.domains.service.AutoAssignResult;
import com.openclaw.manager.openclawserversmanager.domains.service.DomainAssignmentService;
import com.openclaw.manager.openclawserversmanager.domains.service.ServerSslDomainSyncService;
import com.openclaw.manager.openclawserversmanager.domains.service.SslService;
import com.openclaw.manager.openclawserversmanager.ssh.service.SshService;
import com.openclaw.manager.openclawserversmanager.users.service.UserServerAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ServerService {

    private static final Logger log = LoggerFactory.getLogger(ServerService.class);

    private final ServerRepository serverRepository;
    private final SecretService secretService;
    private final AuditService auditService;
    private final SshService sshService;
    private final DomainAssignmentService domainAssignmentService;
    private final SslService sslService;
    private final ServerSslDomainSyncService serverSslDomainSyncService;
    private final UserServerAccessService userServerAccessService;

    public ServerService(ServerRepository serverRepository, SecretService secretService,
                         AuditService auditService, SshService sshService,
                         @Lazy DomainAssignmentService domainAssignmentService,
                         @Lazy SslService sslService,
                         ServerSslDomainSyncService serverSslDomainSyncService,
                         @Lazy UserServerAccessService userServerAccessService) {
        this.serverRepository = serverRepository;
        this.secretService = secretService;
        this.auditService = auditService;
        this.sshService = sshService;
        this.domainAssignmentService = domainAssignmentService;
        this.sslService = sslService;
        this.serverSslDomainSyncService = serverSslDomainSyncService;
        this.userServerAccessService = userServerAccessService;
    }

    @Transactional
    public ServerResponse createServer(CreateServerRequest request, UUID currentUserId) {
        if (serverRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Server with name '" + request.name() + "' already exists");
        }

        if (request.credentialId() != null) {
            SecretResponse secret = secretService.getSecretById(request.credentialId());
            validateCredentialType(request.authType(), secret.type());
        }

        if (request.passphraseCredentialId() != null) {
            SecretResponse passphrase = secretService.getSecretById(request.passphraseCredentialId());
            if (passphrase.type() != SecretType.SSH_PASSWORD) {
                throw new IllegalArgumentException(
                        "Passphrase credential must be of type SSH_PASSWORD, but got " + passphrase.type());
            }
        }

        Server server = ServerMapper.toEntity(request);
        Server saved = serverRepository.save(server);
        ServerSslDomainSyncService.SyncResult syncResult = null;
        if (request.zoneId() == null) {
            syncResult = syncTrackedDomainStateWithResult(saved);
            saved = serverRepository.findById(saved.getId()).orElse(saved);
        }

        try {
            auditService.log(AuditAction.SERVER_CREATED, "SERVER", saved.getId(), currentUserId,
                    "Server '%s' created (env: %s)".formatted(saved.getName(), saved.getEnvironment()));
        } catch (Exception ignored) {
        }

        if (request.zoneId() == null && syncResult != null && syncResult.detected() && syncResult.hostname() != null) {
            return ServerMapper.toResponse(saved);
        }

        // Auto-assign subdomain — async. Returns almost immediately with a job id the
        // frontend uses to poll progress. The DNS provider call does NOT block the
        // server-create response thread anymore.
        UUID pendingAssignmentId = null;
        UUID pendingJobId = null;
        try {
            String ip = saved.getIpAddress() != null ? saved.getIpAddress() : saved.getHostname();
            Optional<AutoAssignResult> result = domainAssignmentService.autoAssignServerDomain(
                    saved.getId(), saved.getName(), ip, request.zoneId(), currentUserId);

            if (result.isPresent()) {
                AutoAssignResult r = result.get();
                pendingAssignmentId = r.assignment().id();
                if (r.job() != null) pendingJobId = r.job().id();
            }
        } catch (Exception e) {
            // No longer throw — client sees the server was created and can reassign later.
            log.warn("Auto-assign domain failed to queue for server '{}': {}", saved.getName(), e.getMessage());
        }

        return ServerMapper.toResponse(saved, pendingAssignmentId, pendingJobId);
    }

    public Page<ServerResponse> getAllServers(Pageable pageable) {
        return serverRepository.findAll(pageable).map(ServerMapper::toResponse);
    }

    public Page<ServerResponse> getAllServers(Pageable pageable, UUID userId, String role) {
        if ("EMPLOYEE".equals(role)) {
            List<UUID> accessibleIds = userServerAccessService.getAccessibleServerIds(userId);
            if (accessibleIds.isEmpty()) return Page.empty(pageable);
            return serverRepository.findByIdIn(accessibleIds, pageable).map(ServerMapper::toResponse);
        }
        return serverRepository.findAll(pageable).map(ServerMapper::toResponse);
    }

    public ServerResponse getServerById(UUID id) {
        Server server = findServerOrThrow(id);
        return ServerMapper.toResponse(server);
    }

    public ServerResponse getServerById(UUID id, UUID userId, String role) {
        checkServerAccess(id, userId, role);
        return ServerMapper.toResponse(findServerOrThrow(id));
    }

    public void checkServerAccess(UUID serverId, UUID userId, String role) {
        if ("EMPLOYEE".equals(role) && !userServerAccessService.hasAccessToServer(userId, serverId)) {
            throw new AccessDeniedException("You do not have access to this server");
        }
    }

    @Transactional
    public ServerResponse updateServer(UUID id, UpdateServerRequest request, UUID currentUserId) {
        Server server = findServerOrThrow(id);

        if (request.name() != null && !request.name().equals(server.getName())) {
            if (serverRepository.existsByName(request.name())) {
                throw new DuplicateResourceException("Server with name '" + request.name() + "' already exists");
            }
            server.setName(request.name());
        }

        if (request.hostname() != null) server.setHostname(request.hostname());
        if (request.ipAddress() != null) server.setIpAddress(request.ipAddress());
        if (request.sshPort() != null) server.setSshPort(request.sshPort());
        if (request.sshUsername() != null) server.setSshUsername(request.sshUsername());
        if (request.environment() != null) server.setEnvironment(request.environment());
        if (request.rootDomain() != null) server.setRootDomain(request.rootDomain());
        if (request.subdomain() != null) server.setSubdomain(request.subdomain());
        if (request.sslEnabled() != null) server.setSslEnabled(request.sslEnabled());
        if (request.metadata() != null) server.setMetadata(ServerMapper.serializeMetadata(request.metadata()));

        if (request.authType() != null) {
            server.setAuthType(request.authType());
        }
        if (request.credentialId() != null) {
            SecretResponse secret = secretService.getSecretById(request.credentialId());
            AuthType effectiveAuthType = request.authType() != null ? request.authType() : server.getAuthType();
            validateCredentialType(effectiveAuthType, secret.type());
            server.setCredentialId(request.credentialId());
        }
        if (request.passphraseCredentialId() != null) {
            SecretResponse passphrase = secretService.getSecretById(request.passphraseCredentialId());
            if (passphrase.type() != SecretType.SSH_PASSWORD) {
                throw new IllegalArgumentException(
                        "Passphrase credential must be of type SSH_PASSWORD, but got " + passphrase.type());
            }
            server.setPassphraseCredentialId(request.passphraseCredentialId());
        }

        Server saved = serverRepository.save(server);
        saved = syncTrackedDomainState(saved);

        try {
            auditService.log(AuditAction.SERVER_UPDATED, "SERVER", saved.getId(), currentUserId,
                    "Server '%s' updated".formatted(saved.getName()));
        } catch (Exception ignored) {
        }

        return ServerMapper.toResponse(saved);
    }

    @Transactional
    public void deleteServer(UUID id, UUID currentUserId) {
        Server server = findServerOrThrow(id);
        String name = server.getName();

        // Remove SSL certificate for this server (best-effort)
        try {
            sslService.removeByServerId(id, currentUserId);
        } catch (Exception e) {
            log.warn("Failed to remove SSL for server {}: {}", id, e.getMessage());
        }

        // Release all domain assignments for this server
        try {
            domainAssignmentService.releaseAllForResource(id, currentUserId);
        } catch (Exception e) {
            log.warn("Failed to release domains for server {}: {}", id, e.getMessage());
        }

        serverRepository.delete(server);

        try {
            auditService.log(AuditAction.SERVER_DELETED, "SERVER", id, currentUserId,
                    "Server '%s' deleted".formatted(name));
        } catch (Exception ignored) {
        }
    }

    @Transactional
    public TestConnectionResponse testConnection(UUID id, UUID currentUserId) {
        Server server = findServerOrThrow(id);
        TestConnectionResult result = sshService.testConnection(server);

        server.setStatus(result.success() ? ServerStatus.ONLINE : ServerStatus.OFFLINE);
        serverRepository.save(server);
        if (result.success()) {
            syncTrackedDomainState(server);
        }

        try {
            auditService.log(AuditAction.SERVER_CONNECTION_TESTED, "SERVER", id, currentUserId,
                    "Connection test on '%s': %s".formatted(server.getName(),
                            result.success() ? "success (%dms)".formatted(result.latencyMs()) : result.message()));
        } catch (Exception ignored) {
        }

        return new TestConnectionResponse(
                result.success(),
                result.message(),
                result.success() ? result.latencyMs() : null
        );
    }

    @Transactional
    public CommandResponse executeCommand(UUID id, String command, Integer timeoutSeconds, UUID currentUserId) {
        Server server = findServerOrThrow(id);

        CommandResult result = timeoutSeconds != null
                ? sshService.executeCommand(server, command, timeoutSeconds)
                : sshService.executeCommand(server, command);

        try {
            auditService.log(AuditAction.SSH_COMMAND_EXECUTED, "SERVER", id, currentUserId,
                    "Command on '%s': exit=%d, duration=%dms".formatted(
                            server.getName(), result.exitCode(), result.durationMs()));
        } catch (Exception ignored) {
        }

        return new CommandResponse(
                result.exitCode(),
                result.stdout(),
                result.stderr(),
                result.durationMs(),
                server.getId(),
                server.getName()
        );
    }

    public Server getServerEntity(UUID id) {
        return findServerOrThrow(id);
    }

    @Transactional
    public void updateSslEnabled(UUID id, boolean sslEnabled) {
        Server server = findServerOrThrow(id);
        server.setSslEnabled(sslEnabled);
        serverRepository.save(server);
    }

    private Server syncTrackedDomainState(Server server) {
        syncTrackedDomainStateWithResult(server);
        return serverRepository.findById(server.getId()).orElse(server);
    }

    private ServerSslDomainSyncService.SyncResult syncTrackedDomainStateWithResult(Server server) {
        try {
            return serverSslDomainSyncService.sync(server);
        } catch (Exception e) {
            log.debug("Server domain/SSL sync skipped for '{}': {}", server.getName(), e.getMessage());
            return new ServerSslDomainSyncService.SyncResult(
                    null,
                    server.isSslEnabled(),
                    null,
                    null,
                    false
            );
        }
    }

    private Server findServerOrThrow(UUID id) {
        return serverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Server with id " + id + " not found"));
    }

    private void validateCredentialType(AuthType authType, SecretType secretType) {
        boolean valid = switch (authType) {
            case PASSWORD -> secretType == SecretType.SSH_PASSWORD;
            case PRIVATE_KEY -> secretType == SecretType.SSH_PRIVATE_KEY;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "Auth type %s requires a secret of type %s, but got %s".formatted(
                            authType,
                            authType == AuthType.PASSWORD ? "SSH_PASSWORD" : "SSH_PRIVATE_KEY",
                            secretType));
        }
    }
}
