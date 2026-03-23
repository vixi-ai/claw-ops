package com.openclaw.manager.openclawserversmanager.domains.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.DuplicateResourceException;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.domains.dto.CreateProviderAccountRequest;
import com.openclaw.manager.openclawserversmanager.domains.dto.ProviderAccountResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.SyncDomainsResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.UpdateProviderAccountRequest;
import com.openclaw.manager.openclawserversmanager.domains.dto.ValidateCredentialsResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.AssignmentStatus;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainAssignment;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainEventAction;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainEventOutcome;
import com.openclaw.manager.openclawserversmanager.domains.entity.HealthStatus;
import com.openclaw.manager.openclawserversmanager.domains.entity.ManagedZone;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProviderAccount;
import com.openclaw.manager.openclawserversmanager.domains.exception.DomainException;
import com.openclaw.manager.openclawserversmanager.domains.mapper.ProviderAccountMapper;
import com.openclaw.manager.openclawserversmanager.domains.provider.DnsProviderAdapter;
import com.openclaw.manager.openclawserversmanager.domains.provider.ProviderAdapterFactory;
import com.openclaw.manager.openclawserversmanager.domains.provider.DiscoveredDomain;
import com.openclaw.manager.openclawserversmanager.domains.provider.ProviderCapabilities;
import com.openclaw.manager.openclawserversmanager.domains.repository.DomainAssignmentRepository;
import com.openclaw.manager.openclawserversmanager.domains.repository.ManagedZoneRepository;
import com.openclaw.manager.openclawserversmanager.domains.repository.ProviderAccountRepository;
import com.openclaw.manager.openclawserversmanager.secrets.dto.SecretResponse;
import com.openclaw.manager.openclawserversmanager.secrets.entity.SecretType;
import com.openclaw.manager.openclawserversmanager.secrets.service.SecretService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProviderAccountService {

    private static final Logger log = LoggerFactory.getLogger(ProviderAccountService.class);

    private final ProviderAccountRepository providerAccountRepository;
    private final ManagedZoneRepository managedZoneRepository;
    private final DomainAssignmentRepository domainAssignmentRepository;
    private final SecretService secretService;
    private final ProviderAdapterFactory providerAdapterFactory;
    private final DomainEventService domainEventService;
    private final AuditService auditService;

    public ProviderAccountService(ProviderAccountRepository providerAccountRepository,
                                   ManagedZoneRepository managedZoneRepository,
                                   DomainAssignmentRepository domainAssignmentRepository,
                                   SecretService secretService,
                                   ProviderAdapterFactory providerAdapterFactory,
                                   DomainEventService domainEventService,
                                   AuditService auditService) {
        this.providerAccountRepository = providerAccountRepository;
        this.managedZoneRepository = managedZoneRepository;
        this.domainAssignmentRepository = domainAssignmentRepository;
        this.secretService = secretService;
        this.providerAdapterFactory = providerAdapterFactory;
        this.domainEventService = domainEventService;
        this.auditService = auditService;
    }

    @Transactional
    public ProviderAccountResponse createAccount(CreateProviderAccountRequest request, UUID userId) {
        if (providerAccountRepository.existsByDisplayName(request.displayName())) {
            throw new DuplicateResourceException("Provider account with name '" + request.displayName() + "' already exists");
        }

        SecretResponse secret = secretService.getSecretById(request.credentialId());
        if (secret.type() != SecretType.DNS_TOKEN) {
            throw new DomainException("Credential must be of type DNS_TOKEN, got: " + secret.type());
        }

        ProviderAccount account = ProviderAccountMapper.toEntity(request);
        ProviderAccount saved = providerAccountRepository.save(account);

        try {
            auditService.log(AuditAction.PROVIDER_ACCOUNT_CREATED, "PROVIDER_ACCOUNT", saved.getId(), userId,
                    "Provider account '%s' created (type: %s)".formatted(saved.getDisplayName(), saved.getProviderType()));
        } catch (Exception ignored) { }

        return ProviderAccountMapper.toResponse(saved);
    }

    @Transactional
    public ProviderAccountResponse updateAccount(UUID id, UpdateProviderAccountRequest request, UUID userId) {
        ProviderAccount account = findAccountOrThrow(id);

        if (request.displayName() != null && !request.displayName().equals(account.getDisplayName())) {
            if (providerAccountRepository.existsByDisplayName(request.displayName())) {
                throw new DuplicateResourceException("Provider account with name '" + request.displayName() + "' already exists");
            }
            account.setDisplayName(request.displayName());
        }

        if (request.enabled() != null) {
            account.setEnabled(request.enabled());
        }

        if (request.credentialId() != null) {
            SecretResponse secret = secretService.getSecretById(request.credentialId());
            if (secret.type() != SecretType.DNS_TOKEN) {
                throw new DomainException("Credential must be of type DNS_TOKEN, got: " + secret.type());
            }
            account.setCredentialId(request.credentialId());
        }

        if (request.providerSettings() != null) {
            account.setProviderSettings(ProviderAccountMapper.serializeSettings(request.providerSettings()));
        }

        ProviderAccount saved = providerAccountRepository.save(account);

        try {
            auditService.log(AuditAction.PROVIDER_ACCOUNT_UPDATED, "PROVIDER_ACCOUNT", saved.getId(), userId,
                    "Provider account '%s' updated".formatted(saved.getDisplayName()));
        } catch (Exception ignored) { }

        return ProviderAccountMapper.toResponse(saved);
    }

    @Transactional
    public void deleteAccount(UUID id, UUID userId) {
        ProviderAccount account = findAccountOrThrow(id);
        String name = account.getDisplayName();

        // Cascade: release assignments and delete zones belonging to this account
        List<ManagedZone> zones = managedZoneRepository.findByProviderAccountId(id);
        int releasedAssignments = 0;
        for (ManagedZone zone : zones) {
            List<DomainAssignment> assignments = domainAssignmentRepository.findByZoneId(zone.getId());
            for (DomainAssignment assignment : assignments) {
                if (assignment.getStatus() != AssignmentStatus.RELEASED) {
                    assignment.setStatus(AssignmentStatus.RELEASED);
                    domainAssignmentRepository.save(assignment);
                    releasedAssignments++;
                }
            }
            domainAssignmentRepository.deleteAll(assignments);
            managedZoneRepository.delete(zone);
        }

        providerAccountRepository.delete(account);

        try {
            auditService.log(AuditAction.PROVIDER_ACCOUNT_DELETED, "PROVIDER_ACCOUNT", id, userId,
                    "Provider account '%s' deleted (cascaded: %d zones, %d assignments released)"
                            .formatted(name, zones.size(), releasedAssignments));
        } catch (Exception ignored) { }
    }

    public ProviderAccountResponse getAccountById(UUID id) {
        return ProviderAccountMapper.toResponse(findAccountOrThrow(id));
    }

    public Page<ProviderAccountResponse> getAllAccounts(Pageable pageable) {
        return providerAccountRepository.findAll(pageable).map(ProviderAccountMapper::toResponse);
    }

    @Transactional
    public ValidateCredentialsResponse validateCredentials(UUID accountId, UUID userId) {
        ProviderAccount account = findAccountOrThrow(accountId);

        String decryptedToken = secretService.decryptSecret(account.getCredentialId());
        Map<String, Object> settings = ProviderAccountMapper.deserializeSettings(account.getProviderSettings());
        DnsProviderAdapter adapter = providerAdapterFactory.getAdapter(account.getProviderType());

        ValidateCredentialsResponse result = adapter.validateCredentials(decryptedToken, settings);

        account.setHealthStatus(result.valid() ? HealthStatus.HEALTHY : HealthStatus.UNREACHABLE);
        providerAccountRepository.save(account);

        domainEventService.recordEvent(null, null, DomainEventAction.CREDENTIALS_VALIDATED,
                result.valid() ? DomainEventOutcome.SUCCESS : DomainEventOutcome.FAILURE,
                null, "Account '%s': %s".formatted(account.getDisplayName(), result.message()));

        // Auto-sync domains after successful validation
        if (result.valid()) {
            try {
                syncDomainsForAccount(accountId, userId);
            } catch (Exception e) {
                log.warn("Auto-sync domains failed for account '{}': {}", account.getDisplayName(), e.getMessage());
            }
        }

        return result;
    }

    public ProviderCapabilities getCapabilities(UUID accountId) {
        ProviderAccount account = findAccountOrThrow(accountId);
        return providerAdapterFactory.getAdapter(account.getProviderType()).getCapabilities();
    }

    @Transactional
    public SyncDomainsResponse syncDomainsForAccount(UUID accountId, UUID userId) {
        ProviderAccount account = findAccountOrThrow(accountId);

        String decryptedToken = secretService.decryptSecret(account.getCredentialId());
        Map<String, Object> settings = ProviderAccountMapper.deserializeSettings(account.getProviderSettings());
        DnsProviderAdapter adapter = providerAdapterFactory.getAdapter(account.getProviderType());

        List<DiscoveredDomain> discovered = adapter.listDomains(decryptedToken, settings);

        int imported = 0;
        int skipped = 0;

        for (DiscoveredDomain d : discovered) {
            if (managedZoneRepository.findByZoneNameAndProviderAccountId(d.domainName(), accountId).isPresent()) {
                skipped++;
                continue;
            }

            ManagedZone zone = new ManagedZone();
            zone.setZoneName(d.domainName());
            zone.setProviderAccountId(accountId);
            zone.setActive(d.manageable());
            zone.setProviderZoneId(d.providerZoneId());
            zone.setDefaultTtl(300);
            managedZoneRepository.save(zone);
            imported++;
        }

        log.info("Synced domains for account '{}': {} discovered, {} imported, {} skipped",
                account.getDisplayName(), discovered.size(), imported, skipped);

        try {
            auditService.log(AuditAction.ZONE_CREATED, "PROVIDER_ACCOUNT", accountId, userId,
                    "Domain sync: %d imported, %d skipped for account '%s'"
                            .formatted(imported, skipped, account.getDisplayName()));
        } catch (Exception ignored) { }

        return new SyncDomainsResponse(discovered.size(), imported, skipped);
    }

    public ProviderAccount findAccountOrThrow(UUID id) {
        return providerAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider account with id " + id + " not found"));
    }
}
