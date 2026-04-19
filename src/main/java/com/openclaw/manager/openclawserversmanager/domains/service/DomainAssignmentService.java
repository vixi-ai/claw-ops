package com.openclaw.manager.openclawserversmanager.domains.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.domains.dto.AssignCustomDomainRequest;
import com.openclaw.manager.openclawserversmanager.domains.dto.AssignServerDomainRequest;
import com.openclaw.manager.openclawserversmanager.domains.dto.DomainAssignmentResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.DomainJobResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.AssignmentStatus;
import com.openclaw.manager.openclawserversmanager.domains.entity.AssignmentType;
import com.openclaw.manager.openclawserversmanager.domains.entity.DnsRecordType;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainAssignment;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainEventAction;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainEventOutcome;
import com.openclaw.manager.openclawserversmanager.domains.entity.ManagedZone;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProviderAccount;
import com.openclaw.manager.openclawserversmanager.domains.exception.DnsProviderException;
import com.openclaw.manager.openclawserversmanager.domains.exception.DomainException;
import com.openclaw.manager.openclawserversmanager.domains.mapper.DomainAssignmentMapper;
import com.openclaw.manager.openclawserversmanager.domains.mapper.ProviderAccountMapper;
import com.openclaw.manager.openclawserversmanager.domains.naming.HostnameStrategy;
import com.openclaw.manager.openclawserversmanager.domains.provider.DnsProviderAdapter;
import com.openclaw.manager.openclawserversmanager.domains.provider.DnsRecord;
import com.openclaw.manager.openclawserversmanager.domains.provider.ProviderAdapterFactory;
import com.openclaw.manager.openclawserversmanager.domains.repository.DomainAssignmentRepository;
import com.openclaw.manager.openclawserversmanager.secrets.service.SecretService;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.service.ServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DomainAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(DomainAssignmentService.class);

    private final DomainAssignmentRepository domainAssignmentRepository;
    private final ManagedZoneService managedZoneService;
    private final ProviderAccountService providerAccountService;
    private final ProviderAdapterFactory providerAdapterFactory;
    private final SecretService secretService;
    private final ServerService serverService;
    private final HostnameStrategy hostnameStrategy;
    private final DomainEventService domainEventService;
    private final AuditService auditService;
    private final ServerSslDomainSyncService serverSslDomainSyncService;
    private final DomainAssignmentOrchestrator domainAssignmentOrchestrator;

    public DomainAssignmentService(DomainAssignmentRepository domainAssignmentRepository,
                                    ManagedZoneService managedZoneService,
                                    ProviderAccountService providerAccountService,
                                    ProviderAdapterFactory providerAdapterFactory,
                                    SecretService secretService,
                                    ServerService serverService,
                                    HostnameStrategy hostnameStrategy,
                                    DomainEventService domainEventService,
                                    AuditService auditService,
                                    ServerSslDomainSyncService serverSslDomainSyncService,
                                    @Lazy DomainAssignmentOrchestrator domainAssignmentOrchestrator) {
        this.domainAssignmentRepository = domainAssignmentRepository;
        this.managedZoneService = managedZoneService;
        this.providerAccountService = providerAccountService;
        this.providerAdapterFactory = providerAdapterFactory;
        this.secretService = secretService;
        this.serverService = serverService;
        this.hostnameStrategy = hostnameStrategy;
        this.domainEventService = domainEventService;
        this.auditService = auditService;
        this.serverSslDomainSyncService = serverSslDomainSyncService;
        this.domainAssignmentOrchestrator = domainAssignmentOrchestrator;
    }

    /**
     * Manual "assign domain to server" entry point. Creates the assignment row in
     * PROVISIONING state and hands off DNS creation to an async job — returns immediately
     * with the job id embedded in the response.
     */
    @Transactional
    public DomainAssignmentResponse assignServerDomain(AssignServerDomainRequest request, UUID userId) {
        Server server = serverService.getServerEntity(request.serverId());
        ManagedZone zone = managedZoneService.findZoneOrThrow(request.zoneId());

        if (!zone.isActive()) {
            throw new DomainException("Zone '%s' is not active. Activate it first.".formatted(zone.getZoneName()));
        }

        String ipAddress = server.getIpAddress();
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = server.getHostname();
        }

        String hostname = request.hostnameOverride() != null && !request.hostnameOverride().isBlank()
                ? request.hostnameOverride()
                : hostnameStrategy.generateServerHostname(server.getName(), zone.getZoneName());

        if (domainAssignmentRepository.findByHostnameAndStatusNot(hostname, AssignmentStatus.RELEASED).isPresent()) {
            throw new DomainException("Hostname '%s' is already assigned".formatted(hostname));
        }

        DomainAssignment assignment = new DomainAssignment();
        assignment.setZoneId(zone.getId());
        assignment.setHostname(hostname);
        assignment.setRecordType(DnsRecordType.A);
        assignment.setTargetValue(ipAddress);
        assignment.setAssignmentType(AssignmentType.SERVER);
        assignment.setResourceId(server.getId());
        assignment.setStatus(AssignmentStatus.PROVISIONING);
        assignment.setDesiredStateHash(computeStateHash("A", ipAddress, zone.getDefaultTtl()));
        assignment = domainAssignmentRepository.saveAndFlush(assignment);

        try {
            auditService.log(AuditAction.DOMAIN_ASSIGNED, "DOMAIN_ASSIGNMENT", assignment.getId(), userId,
                    "Server domain assignment queued: %s → %s".formatted(hostname, ipAddress));
        } catch (Exception ignored) { }

        DomainJobResponse job = domainAssignmentOrchestrator.triggerAssignment(assignment.getId(), userId);
        return DomainAssignmentMapper.toResponse(assignment, zone.getZoneName(), job.id());
    }

    /**
     * Custom DNS record (A/AAAA/CNAME/TXT/...) endpoint. Also async.
     */
    @Transactional
    public DomainAssignmentResponse assignCustomDomain(AssignCustomDomainRequest request, UUID userId) {
        ManagedZone zone = managedZoneService.findZoneOrThrow(request.zoneId());

        if (!zone.isActive()) {
            throw new DomainException("Zone '%s' is not active".formatted(zone.getZoneName()));
        }

        if (domainAssignmentRepository.findByHostnameAndStatusNot(request.hostname(), AssignmentStatus.RELEASED).isPresent()) {
            throw new DomainException("Hostname '%s' is already assigned".formatted(request.hostname()));
        }

        DomainAssignment assignment = new DomainAssignment();
        assignment.setZoneId(zone.getId());
        assignment.setHostname(request.hostname());
        assignment.setRecordType(request.recordType());
        assignment.setTargetValue(request.targetValue());
        assignment.setAssignmentType(AssignmentType.CUSTOM);
        assignment.setResourceId(request.resourceId());
        assignment.setStatus(AssignmentStatus.PROVISIONING);
        assignment.setDesiredStateHash(computeStateHash(request.recordType().name(), request.targetValue(), zone.getDefaultTtl()));
        assignment = domainAssignmentRepository.saveAndFlush(assignment);

        try {
            auditService.log(AuditAction.DOMAIN_ASSIGNED, "DOMAIN_ASSIGNMENT", assignment.getId(), userId,
                    "Custom domain assignment queued: %s".formatted(request.hostname()));
        } catch (Exception ignored) { }

        DomainJobResponse job = domainAssignmentOrchestrator.triggerAssignment(assignment.getId(), userId);
        return DomainAssignmentMapper.toResponse(assignment, zone.getZoneName(), job.id());
    }

    @Transactional
    public void releaseAssignment(UUID assignmentId, UUID userId) {
        DomainAssignment assignment = findAssignmentOrThrow(assignmentId);
        ManagedZone zone = managedZoneService.findZoneOrThrow(assignment.getZoneId());

        if (assignment.getStatus() == AssignmentStatus.RELEASED) {
            return; // Already released
        }

        assignment.setStatus(AssignmentStatus.RELEASING);
        domainAssignmentRepository.save(assignment);

        if (assignment.getProviderRecordId() != null) {
            ProviderAccount account = providerAccountService.findAccountOrThrow(zone.getProviderAccountId());
            String decryptedToken = secretService.decryptSecret(account.getCredentialId());
            Map<String, Object> settings = ProviderAccountMapper.deserializeSettings(account.getProviderSettings());
            DnsProviderAdapter adapter = providerAdapterFactory.getAdapter(account.getProviderType());

            try {
                adapter.deleteRecord(zone.getProviderZoneId(), assignment.getProviderRecordId(), decryptedToken, settings);

                domainEventService.recordEvent(assignment.getId(), zone.getId(),
                        DomainEventAction.RECORD_DELETED, DomainEventOutcome.SUCCESS,
                        assignment.getProviderRecordId(), "DNS record deleted: %s".formatted(assignment.getHostname()));
            } catch (DnsProviderException e) {
                domainEventService.recordEvent(assignment.getId(), zone.getId(),
                        DomainEventAction.RECORD_DELETED, DomainEventOutcome.FAILURE,
                        e.getProviderCorrelationId(), e.getMessage());
                // Still mark as released — we tried our best
            }
        }

        assignment.setStatus(AssignmentStatus.RELEASED);
        domainAssignmentRepository.save(assignment);

        try {
            auditService.log(AuditAction.DOMAIN_RELEASED, "DOMAIN_ASSIGNMENT", assignmentId, userId,
                    "Domain released: %s".formatted(assignment.getHostname()));
        } catch (Exception ignored) { }
    }

    @Transactional
    public void releaseAllForResource(UUID resourceId, UUID userId) {
        List<DomainAssignment> assignments = domainAssignmentRepository
                .findByResourceIdAndStatusNot(resourceId, AssignmentStatus.RELEASED);
        for (DomainAssignment assignment : assignments) {
            releaseAssignment(assignment.getId(), userId);
        }
    }

    /**
     * Synchronous manual verification. Includes one quick retry — when a user clicks
     * "Verify" seconds after the DNS record was created, the provider's list endpoint
     * occasionally hasn't yet surfaced it.
     */
    @Transactional
    public DomainAssignmentResponse verifyAssignment(UUID assignmentId, UUID userId) {
        DomainAssignment assignment = findAssignmentOrThrow(assignmentId);
        ManagedZone zone = managedZoneService.findZoneOrThrow(assignment.getZoneId());
        ProviderAccount account = providerAccountService.findAccountOrThrow(zone.getProviderAccountId());

        String decryptedToken = secretService.decryptSecret(account.getCredentialId());
        Map<String, Object> settings = ProviderAccountMapper.deserializeSettings(account.getProviderSettings());
        DnsProviderAdapter adapter = providerAdapterFactory.getAdapter(account.getProviderType());

        boolean found = checkRecordListed(adapter, zone, assignment, decryptedToken, settings);
        if (!found) {
            try { Thread.sleep(2_000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            found = checkRecordListed(adapter, zone, assignment, decryptedToken, settings);
        }

        if (found) {
            assignment.setStatus(AssignmentStatus.VERIFIED);
            domainEventService.recordEvent(assignment.getId(), zone.getId(),
                    DomainEventAction.RECORD_VERIFIED, DomainEventOutcome.SUCCESS,
                    assignment.getProviderRecordId(), "DNS record verified for %s".formatted(assignment.getHostname()));
        } else {
            assignment.setStatus(AssignmentStatus.FAILED);
            domainEventService.recordEvent(assignment.getId(), zone.getId(),
                    DomainEventAction.RECORD_VERIFIED, DomainEventOutcome.FAILURE,
                    null, "DNS record not found for %s".formatted(assignment.getHostname()));
        }

        domainAssignmentRepository.save(assignment);

        try {
            auditService.log(AuditAction.DOMAIN_VERIFIED, "DOMAIN_ASSIGNMENT", assignmentId, userId,
                    "Domain verification %s: %s".formatted(found ? "passed" : "failed", assignment.getHostname()));
        } catch (Exception ignored) { }

        return DomainAssignmentMapper.toResponse(assignment, zone.getZoneName());
    }

    private boolean checkRecordListed(DnsProviderAdapter adapter, ManagedZone zone, DomainAssignment assignment,
                                       String decryptedToken, Map<String, Object> settings) {
        try {
            List<DnsRecord> records = adapter.listRecords(zone.getProviderZoneId(), decryptedToken, settings);
            return records.stream().anyMatch(r ->
                    r.hostname().equalsIgnoreCase(assignment.getHostname()) &&
                    r.type() == assignment.getRecordType() &&
                    r.value().equals(assignment.getTargetValue()));
        } catch (Exception e) {
            log.debug("Verify listRecords failed for {}: {}", assignment.getHostname(), e.getMessage());
            return false;
        }
    }

    public DomainAssignmentResponse getAssignment(UUID id) {
        DomainAssignment assignment = findAssignmentOrThrow(id);
        ManagedZone zone = managedZoneService.findZoneOrThrow(assignment.getZoneId());
        UUID latestJobId = domainAssignmentOrchestrator.getLatestJobForAssignment(id)
                .map(DomainJobResponse::id).orElse(null);
        return DomainAssignmentMapper.toResponse(assignment, zone.getZoneName(), latestJobId);
    }

    public Page<DomainAssignmentResponse> getAllAssignments(Pageable pageable) {
        return domainAssignmentRepository.findAll(pageable).map(a -> {
            ManagedZone zone = managedZoneService.findZoneOrThrow(a.getZoneId());
            return DomainAssignmentMapper.toResponse(a, zone.getZoneName());
        });
    }

    public List<DomainAssignmentResponse> getAssignmentsForZone(UUID zoneId) {
        ManagedZone zone = managedZoneService.findZoneOrThrow(zoneId);
        return domainAssignmentRepository.findByZoneId(zoneId).stream()
                .map(a -> DomainAssignmentMapper.toResponse(a, zone.getZoneName()))
                .toList();
    }

    public List<DomainAssignmentResponse> getAssignmentsForResource(UUID resourceId) {
        return domainAssignmentRepository.findByResourceId(resourceId).stream()
                .map(a -> {
                    ManagedZone zone = managedZoneService.findZoneOrThrow(a.getZoneId());
                    return DomainAssignmentMapper.toResponse(a, zone.getZoneName());
                })
                .toList();
    }

    /**
     * Auto-assign using default zone (backward compat). Returns empty when no default zone is set.
     */
    @Transactional
    public Optional<AutoAssignResult> autoAssignServerDomain(
            UUID serverId, String serverName, String serverIp, UUID userId) {
        return autoAssignServerDomain(serverId, serverName, serverIp, null, userId);
    }

    /**
     * Auto-assign a subdomain for a server. If zoneId is provided, uses that specific zone
     * (throws on failure). If zoneId is null, falls back to the default auto-assign zone
     * (best-effort, returns empty on failure).
     *
     * The DNS provider call is deferred to a {@link DomainAssignmentJob} that the runner
     * executes asynchronously — this method returns in a few ms.
     */
    @Transactional
    public Optional<AutoAssignResult> autoAssignServerDomain(
            UUID serverId, String serverName, String serverIp, UUID zoneId, UUID userId) {
        ManagedZone zone;

        if (zoneId != null) {
            zone = managedZoneService.findZoneOrThrow(zoneId);
            if (!zone.isActive()) {
                throw new DomainException("Zone '%s' is not active. Activate it first.".formatted(zone.getZoneName()));
            }
        } else {
            Optional<ManagedZone> zoneOpt = managedZoneService.getDefaultAutoAssignZone();
            if (zoneOpt.isEmpty()) {
                log.debug("No default auto-assign zone configured, skipping for server '{}'", serverName);
                return Optional.empty();
            }
            zone = zoneOpt.get();
            if (!zone.isActive()) {
                log.debug("Default auto-assign zone '{}' is not active, skipping", zone.getZoneName());
                return Optional.empty();
            }
        }

        return doAutoAssign(serverId, serverName, serverIp, zone, userId);
    }

    private Optional<AutoAssignResult> doAutoAssign(
            UUID serverId, String serverName, String serverIp, ManagedZone zone, UUID userId) {
        Server server = serverService.getServerEntity(serverId);
        Optional<DomainAssignment> reusableAssignment = findReusableAssignment(server, zone);
        if (reusableAssignment.isPresent()) {
            DomainAssignment existing = reusableAssignment.get();
            DomainAssignmentResponse assignmentResp =
                    DomainAssignmentMapper.toResponse(existing, zone.getZoneName());
            Optional<DomainJobResponse> latest = domainAssignmentOrchestrator.getLatestJobForAssignment(existing.getId());
            return Optional.of(new AutoAssignResult(assignmentResp, latest.orElse(null)));
        }

        String hostname = resolveUniqueHostname(server, serverName, zone.getZoneName());
        if (hostname == null) {
            log.warn("Could not generate unique hostname for server '{}' in zone '{}'",
                    serverName, zone.getZoneName());
            return Optional.empty();
        }

        DomainAssignment assignment = new DomainAssignment();
        assignment.setZoneId(zone.getId());
        assignment.setHostname(hostname);
        assignment.setRecordType(DnsRecordType.A);
        assignment.setTargetValue(serverIp);
        assignment.setAssignmentType(AssignmentType.SERVER);
        assignment.setResourceId(serverId);
        assignment.setStatus(AssignmentStatus.PROVISIONING);
        assignment.setDesiredStateHash(computeStateHash("A", serverIp, zone.getDefaultTtl()));
        assignment = domainAssignmentRepository.saveAndFlush(assignment);

        try {
            auditService.log(AuditAction.DOMAIN_AUTO_ASSIGNED, "DOMAIN_ASSIGNMENT", assignment.getId(), userId,
                    "Auto-assign queued: %s → %s".formatted(hostname, serverIp));
        } catch (Exception ignored) { }

        DomainJobResponse job;
        try {
            job = domainAssignmentOrchestrator.triggerAssignment(assignment.getId(), userId);
        } catch (Exception e) {
            log.error("Failed to trigger async domain assignment for '{}': {}", serverName, e.getMessage());
            assignment.setStatus(AssignmentStatus.FAILED);
            domainAssignmentRepository.save(assignment);
            return Optional.of(new AutoAssignResult(
                    DomainAssignmentMapper.toResponse(assignment, zone.getZoneName()),
                    null
            ));
        }

        return Optional.of(new AutoAssignResult(
                DomainAssignmentMapper.toResponse(assignment, zone.getZoneName(), job.id()),
                job
        ));
    }

    private Optional<DomainAssignment> findReusableAssignment(Server server, ManagedZone zone) {
        String currentHostname = buildAssignedDomain(server);
        if (currentHostname != null && belongsToZone(currentHostname, zone.getZoneName())) {
            Optional<DomainAssignment> byHostname = domainAssignmentRepository
                    .findByHostnameAndStatusNot(currentHostname, AssignmentStatus.RELEASED);
            if (byHostname.isPresent()) {
                DomainAssignment assignment = byHostname.get();
                if (assignment.getAssignmentType() == AssignmentType.SERVER
                        && (assignment.getResourceId() == null || server.getId().equals(assignment.getResourceId()))) {
                    if (assignment.getResourceId() == null) {
                        assignment.setResourceId(server.getId());
                        assignment = domainAssignmentRepository.save(assignment);
                    }
                    return Optional.of(assignment);
                }
            }
        }

        return domainAssignmentRepository.findByResourceIdAndStatusNot(server.getId(), AssignmentStatus.RELEASED).stream()
                .filter(assignment -> assignment.getAssignmentType() == AssignmentType.SERVER)
                .filter(assignment -> zone.getId().equals(assignment.getZoneId()))
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .findFirst();
    }

    private String resolveUniqueHostname(Server server, String serverName, String zoneName) {
        String base = hostnameStrategy.generateServerHostname(serverName, zoneName);
        if (isHostnameAvailableForServer(server, base)) return base;

        String slug = slugify(serverName);
        for (int i = 2; i <= 99; i++) {
            String candidate = slug + "-" + i + "." + zoneName;
            if (isHostnameAvailableForServer(server, candidate)) return candidate;
        }
        return null;
    }

    private boolean isHostnameAvailableForServer(Server server, String hostname) {
        return domainAssignmentRepository.findByHostnameAndStatusNot(hostname, AssignmentStatus.RELEASED)
                .filter(assignment -> assignment.getResourceId() != null && !server.getId().equals(assignment.getResourceId()))
                .isEmpty()
                && !serverSslDomainSyncService.hasExternalConflict(server, hostname);
    }

    private String buildAssignedDomain(Server server) {
        if (server.getRootDomain() == null || server.getRootDomain().isBlank()) {
            return null;
        }
        if (server.getSubdomain() == null || server.getSubdomain().isBlank()) {
            return server.getRootDomain();
        }
        return server.getSubdomain() + "." + server.getRootDomain();
    }

    private boolean belongsToZone(String hostname, String zoneName) {
        String normalizedHostname = hostname.toLowerCase();
        String normalizedZone = zoneName.toLowerCase();
        return normalizedHostname.equals(normalizedZone) || normalizedHostname.endsWith("." + normalizedZone);
    }

    private String slugify(String input) {
        if (input == null || input.isBlank()) return "";
        return input.toLowerCase()
                .replaceAll("[\\s_]+", "-")
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }

    private DomainAssignment findAssignmentOrThrow(UUID id) {
        return domainAssignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Domain assignment with id " + id + " not found"));
    }

    private String computeStateHash(String recordType, String targetValue, int ttl) {
        try {
            String input = "%s|%s|%d".formatted(recordType, targetValue, ttl);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
