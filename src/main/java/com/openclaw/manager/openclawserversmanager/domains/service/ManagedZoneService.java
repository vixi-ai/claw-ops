package com.openclaw.manager.openclawserversmanager.domains.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.DuplicateResourceException;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.domains.dto.CreateManagedZoneRequest;
import com.openclaw.manager.openclawserversmanager.domains.dto.ManagedZoneResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.UpdateManagedZoneRequest;
import com.openclaw.manager.openclawserversmanager.domains.dto.VerifyZoneResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.AssignmentStatus;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainEventAction;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainEventOutcome;
import com.openclaw.manager.openclawserversmanager.domains.entity.ManagedZone;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProviderAccount;
import com.openclaw.manager.openclawserversmanager.domains.exception.DomainException;
import com.openclaw.manager.openclawserversmanager.domains.mapper.ManagedZoneMapper;
import com.openclaw.manager.openclawserversmanager.domains.mapper.ProviderAccountMapper;
import com.openclaw.manager.openclawserversmanager.domains.provider.DnsProviderAdapter;
import com.openclaw.manager.openclawserversmanager.domains.provider.ProviderAdapterFactory;
import com.openclaw.manager.openclawserversmanager.domains.repository.DomainAssignmentRepository;
import com.openclaw.manager.openclawserversmanager.domains.repository.ManagedZoneRepository;
import com.openclaw.manager.openclawserversmanager.secrets.service.SecretService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ManagedZoneService {

    private final ManagedZoneRepository managedZoneRepository;
    private final DomainAssignmentRepository domainAssignmentRepository;
    private final ProviderAccountService providerAccountService;
    private final ProviderAdapterFactory providerAdapterFactory;
    private final SecretService secretService;
    private final DomainEventService domainEventService;
    private final AuditService auditService;

    public ManagedZoneService(ManagedZoneRepository managedZoneRepository,
                               DomainAssignmentRepository domainAssignmentRepository,
                               ProviderAccountService providerAccountService,
                               ProviderAdapterFactory providerAdapterFactory,
                               SecretService secretService,
                               DomainEventService domainEventService,
                               AuditService auditService) {
        this.managedZoneRepository = managedZoneRepository;
        this.domainAssignmentRepository = domainAssignmentRepository;
        this.providerAccountService = providerAccountService;
        this.providerAdapterFactory = providerAdapterFactory;
        this.secretService = secretService;
        this.domainEventService = domainEventService;
        this.auditService = auditService;
    }

    @Transactional
    public ManagedZoneResponse createZone(CreateManagedZoneRequest request, UUID userId) {
        ProviderAccount account = providerAccountService.findAccountOrThrow(request.providerAccountId());

        if (!account.isEnabled()) {
            throw new DomainException("Provider account '%s' is disabled".formatted(account.getDisplayName()));
        }

        if (managedZoneRepository.findByZoneNameAndProviderAccountId(
                request.zoneName().toLowerCase(), request.providerAccountId()).isPresent()) {
            throw new DuplicateResourceException(
                    "Zone '%s' already exists for this provider account".formatted(request.zoneName()));
        }

        ManagedZone zone = ManagedZoneMapper.toEntity(request);
        ManagedZone saved = managedZoneRepository.save(zone);

        try {
            auditService.log(AuditAction.ZONE_CREATED, "MANAGED_ZONE", saved.getId(), userId,
                    "Zone '%s' created for provider '%s'".formatted(saved.getZoneName(), account.getDisplayName()));
        } catch (Exception ignored) { }

        return ManagedZoneMapper.toResponse(saved);
    }

    @Transactional
    public ManagedZoneResponse activateZone(UUID zoneId, UUID userId) {
        ManagedZone zone = findZoneOrThrow(zoneId);
        ProviderAccount account = providerAccountService.findAccountOrThrow(zone.getProviderAccountId());

        String decryptedToken = secretService.decryptSecret(account.getCredentialId());
        Map<String, Object> settings = ProviderAccountMapper.deserializeSettings(account.getProviderSettings());
        DnsProviderAdapter adapter = providerAdapterFactory.getAdapter(account.getProviderType());

        // Run preflight verification
        VerifyZoneResponse verification = adapter.verifyZoneManageable(zone.getZoneName(), decryptedToken, settings);

        if (!verification.manageable()) {
            domainEventService.recordEvent(null, zone.getId(), DomainEventAction.ZONE_PREFLIGHT_FAILED,
                    DomainEventOutcome.FAILURE, null, verification.message());
            throw new DomainException("Zone preflight failed: " + verification.message());
        }

        // Resolve and cache provider zone ID
        String providerZoneId = adapter.resolveZoneId(zone.getZoneName(), decryptedToken, settings);
        zone.setProviderZoneId(providerZoneId);
        zone.setActive(true);
        ManagedZone saved = managedZoneRepository.save(zone);

        domainEventService.recordEvent(null, zone.getId(), DomainEventAction.ZONE_PREFLIGHT_PASSED,
                DomainEventOutcome.SUCCESS, providerZoneId, verification.message());

        try {
            auditService.log(AuditAction.ZONE_ACTIVATED, "MANAGED_ZONE", saved.getId(), userId,
                    "Zone '%s' activated (provider zone ID: %s)".formatted(saved.getZoneName(), providerZoneId));
        } catch (Exception ignored) { }

        return ManagedZoneMapper.toResponse(saved);
    }

    @Transactional
    public ManagedZoneResponse updateZone(UUID id, UpdateManagedZoneRequest request, UUID userId) {
        ManagedZone zone = findZoneOrThrow(id);

        if (request.active() != null) {
            zone.setActive(request.active());
        }
        if (request.defaultTtl() != null) {
            zone.setDefaultTtl(request.defaultTtl());
        }
        if (request.environmentTag() != null) {
            zone.setEnvironmentTag(request.environmentTag());
        }
        if (request.metadata() != null) {
            zone.setMetadata(ManagedZoneMapper.serializeMetadata(request.metadata()));
        }

        ManagedZone saved = managedZoneRepository.save(zone);
        return ManagedZoneMapper.toResponse(saved);
    }

    @Transactional
    public void deleteZone(UUID id, UUID userId) {
        ManagedZone zone = findZoneOrThrow(id);

        if (domainAssignmentRepository.existsByZoneIdAndStatusNot(id, AssignmentStatus.RELEASED)) {
            throw new DomainException("Cannot delete zone — it has active domain assignments. Release them first.");
        }

        String name = zone.getZoneName();
        managedZoneRepository.delete(zone);

        try {
            auditService.log(AuditAction.ZONE_DELETED, "MANAGED_ZONE", id, userId,
                    "Zone '%s' deleted".formatted(name));
        } catch (Exception ignored) { }
    }

    public ManagedZoneResponse getZoneById(UUID id) {
        return ManagedZoneMapper.toResponse(findZoneOrThrow(id));
    }

    public Page<ManagedZoneResponse> getAllZones(Pageable pageable) {
        return managedZoneRepository.findAll(pageable).map(ManagedZoneMapper::toResponse);
    }

    public List<ManagedZoneResponse> getZonesForAccount(UUID accountId) {
        return managedZoneRepository.findByProviderAccountId(accountId).stream()
                .map(ManagedZoneMapper::toResponse)
                .toList();
    }

    @Transactional
    public ManagedZoneResponse setDefaultForAutoAssign(UUID zoneId, UUID userId) {
        ManagedZone zone = findZoneOrThrow(zoneId);

        if (!zone.isActive()) {
            throw new DomainException("Zone '%s' must be active before setting as auto-assign default".formatted(zone.getZoneName()));
        }

        managedZoneRepository.clearDefaultAutoAssign();
        zone.setDefaultForAutoAssign(true);
        ManagedZone saved = managedZoneRepository.save(zone);

        try {
            auditService.log(AuditAction.ZONE_ACTIVATED, "MANAGED_ZONE", saved.getId(), userId,
                    "Zone '%s' set as default for auto-assign".formatted(saved.getZoneName()));
        } catch (Exception ignored) { }

        return ManagedZoneMapper.toResponse(saved);
    }

    public Optional<ManagedZone> getDefaultAutoAssignZone() {
        return managedZoneRepository.findByDefaultForAutoAssignTrue();
    }

    public ManagedZone findZoneOrThrow(UUID id) {
        return managedZoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Managed zone with id " + id + " not found"));
    }
}
