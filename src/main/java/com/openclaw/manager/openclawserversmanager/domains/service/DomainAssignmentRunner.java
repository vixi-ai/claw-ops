package com.openclaw.manager.openclawserversmanager.domains.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.domains.entity.AssignmentStatus;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainAssignment;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainAssignmentJob;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainEventAction;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainEventOutcome;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainJobStatus;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainJobStep;
import com.openclaw.manager.openclawserversmanager.domains.entity.ManagedZone;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProviderAccount;
import com.openclaw.manager.openclawserversmanager.domains.exception.DnsProviderException;
import com.openclaw.manager.openclawserversmanager.domains.mapper.ProviderAccountMapper;
import com.openclaw.manager.openclawserversmanager.domains.provider.DnsProviderAdapter;
import com.openclaw.manager.openclawserversmanager.domains.provider.DnsRecord;
import com.openclaw.manager.openclawserversmanager.domains.provider.ProviderAdapterFactory;
import com.openclaw.manager.openclawserversmanager.domains.repository.DomainAssignmentJobRepository;
import com.openclaw.manager.openclawserversmanager.domains.repository.DomainAssignmentRepository;
import com.openclaw.manager.openclawserversmanager.secrets.service.SecretService;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.repository.ServerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Async runner for domain assignment jobs. Mirrors {@link ProvisioningRunner} for SSL:
 * creates the DNS record via provider adapter, verifies propagation, updates the
 * Server entity's rootDomain/subdomain fields, and transitions job/assignment state.
 */
@Service
public class DomainAssignmentRunner {

    private static final Logger log = LoggerFactory.getLogger(DomainAssignmentRunner.class);

    // Verification configuration: total ~= 10 * 3s = 30s window. DNS providers typically
    // propagate their own records to their own API within seconds.
    private static final int VERIFY_MAX_ATTEMPTS = 10;
    private static final long VERIFY_BACKOFF_MS = 3_000L;

    private final DomainAssignmentJobRepository jobRepository;
    private final DomainAssignmentRepository assignmentRepository;
    private final ManagedZoneService managedZoneService;
    private final ProviderAccountService providerAccountService;
    private final ProviderAdapterFactory providerAdapterFactory;
    private final SecretService secretService;
    private final DomainEventService domainEventService;
    private final ServerRepository serverRepository;
    private final AuditService auditService;
    private final ProvisioningOrchestrator provisioningOrchestrator;

    public DomainAssignmentRunner(DomainAssignmentJobRepository jobRepository,
                                  DomainAssignmentRepository assignmentRepository,
                                  ManagedZoneService managedZoneService,
                                  ProviderAccountService providerAccountService,
                                  ProviderAdapterFactory providerAdapterFactory,
                                  SecretService secretService,
                                  DomainEventService domainEventService,
                                  ServerRepository serverRepository,
                                  AuditService auditService,
                                  @Lazy ProvisioningOrchestrator provisioningOrchestrator) {
        this.jobRepository = jobRepository;
        this.assignmentRepository = assignmentRepository;
        this.managedZoneService = managedZoneService;
        this.providerAccountService = providerAccountService;
        this.providerAdapterFactory = providerAdapterFactory;
        this.secretService = secretService;
        this.domainEventService = domainEventService;
        this.serverRepository = serverRepository;
        this.auditService = auditService;
        this.provisioningOrchestrator = provisioningOrchestrator;
    }

    @Async("provisioningExecutor")
    public void run(UUID jobId) {
        DomainAssignmentJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() == DomainJobStatus.CANCELLED) {
            return;
        }

        DomainAssignment assignment = assignmentRepository.findById(job.getDomainAssignmentId()).orElse(null);
        if (assignment == null) {
            failJob(job, DomainJobStep.FAILED_PERMANENT, "Domain assignment not found");
            return;
        }

        ManagedZone zone;
        try {
            zone = managedZoneService.findZoneOrThrow(assignment.getZoneId());
        } catch (Exception e) {
            failJob(job, DomainJobStep.FAILED_PERMANENT, "Zone not found: " + e.getMessage());
            return;
        }

        job.setStartedAt(Instant.now());
        job.appendLog("Starting domain assignment for " + assignment.getHostname());
        jobRepository.save(job);

        boolean sslAutoTrigger = false;

        try {
            // Step 1: Create DNS record via provider
            if (isCancelled(job)) return;
            updateStep(job, DomainJobStep.CREATING_RECORD, "Creating DNS record via "
                    + zone.getProviderAccountId());

            ProviderAccount account = providerAccountService.findAccountOrThrow(zone.getProviderAccountId());
            String decryptedToken = secretService.decryptSecret(account.getCredentialId());
            Map<String, Object> settings = ProviderAccountMapper.deserializeSettings(account.getProviderSettings());
            DnsProviderAdapter adapter = providerAdapterFactory.getAdapter(account.getProviderType());

            DnsRecord dnsRecord = new DnsRecord(null, assignment.getHostname(), assignment.getRecordType(),
                    assignment.getTargetValue(), zone.getDefaultTtl(), false);
            DnsRecord created;
            try {
                created = adapter.createOrUpsertRecord(zone.getZoneName(), zone.getProviderZoneId(),
                        dnsRecord, decryptedToken, settings);
            } catch (DnsProviderException e) {
                domainEventService.recordEvent(assignment.getId(), zone.getId(),
                        DomainEventAction.RECORD_CREATED, DomainEventOutcome.FAILURE,
                        e.getProviderCorrelationId(), e.getMessage());
                boolean retryable = isRetryableDnsError(e);
                assignment.setStatus(AssignmentStatus.FAILED);
                assignmentRepository.save(assignment);
                failJob(job, retryable ? DomainJobStep.FAILED_RETRYABLE : DomainJobStep.FAILED_PERMANENT,
                        "DNS provider error: " + e.getMessage());
                return;
            }

            assignment.setProviderRecordId(created.providerRecordId());
            assignment.setStatus(AssignmentStatus.DNS_CREATED);
            assignmentRepository.save(assignment);

            domainEventService.recordEvent(assignment.getId(), zone.getId(),
                    DomainEventAction.RECORD_CREATED, DomainEventOutcome.SUCCESS,
                    created.providerRecordId(),
                    "DNS record created: %s → %s".formatted(assignment.getHostname(), assignment.getTargetValue()));

            updateStep(job, DomainJobStep.DNS_CREATED, "DNS record created (providerRecordId="
                    + created.providerRecordId() + ")");

            // Step 2: Verify propagation (at the provider's own API first — DNS can take longer to
            // propagate to public resolvers, but the provider's API is the authoritative view).
            if (isCancelled(job)) return;
            updateStep(job, DomainJobStep.VERIFYING, "Verifying record is listable at provider");

            boolean verified = verifyPropagation(adapter, zone, assignment, decryptedToken, settings, job);
            if (!verified) {
                // Record created but not visible yet — treat as retryable rather than permanent.
                assignment.setStatus(AssignmentStatus.DNS_CREATED);
                assignmentRepository.save(assignment);
                failJob(job, DomainJobStep.FAILED_RETRYABLE,
                        "DNS record not yet listable after %d attempts".formatted(VERIFY_MAX_ATTEMPTS));
                return;
            }

            assignment.setStatus(AssignmentStatus.VERIFIED);
            assignmentRepository.save(assignment);

            domainEventService.recordEvent(assignment.getId(), zone.getId(),
                    DomainEventAction.RECORD_VERIFIED, DomainEventOutcome.SUCCESS,
                    assignment.getProviderRecordId(),
                    "DNS record verified for " + assignment.getHostname());

            updateStep(job, DomainJobStep.VERIFIED, "DNS record verified");

            // Step 3: Update Server entity's rootDomain/subdomain so the UI picks it up
            if (job.getServerId() != null) {
                Optional<Server> serverOpt = serverRepository.findById(job.getServerId());
                if (serverOpt.isPresent()) {
                    Server server = serverOpt.get();
                    String zoneName = zone.getZoneName();
                    String hostname = assignment.getHostname();
                    String subdomain = hostname.equals(zoneName)
                            ? ""
                            : hostname.endsWith("." + zoneName)
                                    ? hostname.substring(0, hostname.length() - zoneName.length() - 1)
                                    : hostname;
                    server.setRootDomain(zoneName);
                    server.setSubdomain(subdomain);
                    serverRepository.save(server);
                    job.appendLog("Updated server '%s' rootDomain=%s subdomain=%s"
                            .formatted(server.getName(), zoneName, subdomain));
                    sslAutoTrigger = true;
                }
            }

            // Step 4: Complete
            job.setCurrentStep(DomainJobStep.COMPLETED);
            job.setStatus(DomainJobStatus.COMPLETED);
            job.setFinishedAt(Instant.now());
            job.appendLog("Domain assignment completed successfully");
            jobRepository.save(job);

            try {
                auditService.log(AuditAction.DOMAIN_AUTO_ASSIGNED, "DOMAIN_ASSIGNMENT", assignment.getId(),
                        job.getTriggeredBy(),
                        "Async domain assignment completed: %s → %s".formatted(
                                assignment.getHostname(), assignment.getTargetValue()));
            } catch (Exception ignored) { }

            log.info("Domain assignment job {} completed for hostname {}", jobId, assignment.getHostname());

        } catch (Exception e) {
            failJob(job, DomainJobStep.FAILED_RETRYABLE, "Unexpected error: " + e.getMessage());
            log.error("Domain assignment job {} failed unexpectedly: {}", jobId, e.getMessage(), e);
        }

        // Best-effort async SSL provisioning once domain is verified. Triggered outside the main
        // try/catch so SSL trigger failures don't mark the domain job FAILED.
        if (sslAutoTrigger) {
            try {
                provisioningOrchestrator.triggerProvisioning(assignment.getId(), null, job.getTriggeredBy());
                job.appendLog("Auto-triggered SSL provisioning");
                jobRepository.save(job);
            } catch (Exception e) {
                log.warn("Auto-trigger SSL provisioning failed for {}: {}", assignment.getHostname(), e.getMessage());
            }
        }
    }

    private boolean verifyPropagation(DnsProviderAdapter adapter, ManagedZone zone, DomainAssignment assignment,
                                      String decryptedToken, Map<String, Object> settings, DomainAssignmentJob job) {
        for (int attempt = 1; attempt <= VERIFY_MAX_ATTEMPTS; attempt++) {
            if (isCancelled(job)) return false;
            try {
                List<DnsRecord> records = adapter.listRecords(zone.getProviderZoneId(), decryptedToken, settings);
                boolean found = records.stream().anyMatch(r ->
                        r.hostname().equalsIgnoreCase(assignment.getHostname()) &&
                        r.type() == assignment.getRecordType() &&
                        r.value().equals(assignment.getTargetValue()));
                if (found) {
                    job.appendLog("Verify attempt #%d: record found".formatted(attempt));
                    jobRepository.save(job);
                    return true;
                }
                job.appendLog("Verify attempt #%d: record not listed yet, sleeping %dms"
                        .formatted(attempt, VERIFY_BACKOFF_MS));
                jobRepository.save(job);
            } catch (Exception e) {
                job.appendLog("Verify attempt #%d failed: %s".formatted(attempt, e.getMessage()));
                jobRepository.save(job);
            }
            try {
                Thread.sleep(VERIFY_BACKOFF_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean isRetryableDnsError(DnsProviderException e) {
        String msg = e.getMessage();
        if (msg == null) return true;
        String lower = msg.toLowerCase();
        return !lower.contains("unauthorized") && !lower.contains("forbidden")
                && !lower.contains("invalid") && !lower.contains("already exists");
    }

    private void updateStep(DomainAssignmentJob job, DomainJobStep step, String message) {
        job.setCurrentStep(step);
        job.appendLog("[%s] %s".formatted(step.name(), message));
        jobRepository.save(job);
        log.info("Domain job {} step {}: {}", job.getId(), step, message);
    }

    private boolean isCancelled(DomainAssignmentJob job) {
        return jobRepository.findById(job.getId())
                .map(j -> j.getStatus() == DomainJobStatus.CANCELLED)
                .orElse(true);
    }

    private void failJob(DomainAssignmentJob job, DomainJobStep failStep, String errorMessage) {
        job.setCurrentStep(failStep);
        job.setStatus(DomainJobStatus.FAILED);
        job.setErrorMessage(errorMessage);
        job.setFinishedAt(Instant.now());
        job.appendLog("[FAILED] " + errorMessage);
        jobRepository.save(job);
        log.error("Domain job {} failed at step {}: {}", job.getId(), failStep, errorMessage);
    }
}
