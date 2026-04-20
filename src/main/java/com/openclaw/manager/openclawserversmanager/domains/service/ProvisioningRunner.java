package com.openclaw.manager.openclawserversmanager.domains.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.domains.config.SslConfig;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainAssignment;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProvisioningJob;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProvisioningJobStatus;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProvisioningStep;
import com.openclaw.manager.openclawserversmanager.domains.entity.SslCertificate;
import com.openclaw.manager.openclawserversmanager.domains.entity.SslStatus;
import com.openclaw.manager.openclawserversmanager.domains.repository.DomainAssignmentRepository;
import com.openclaw.manager.openclawserversmanager.domains.repository.ProvisioningJobRepository;
import com.openclaw.manager.openclawserversmanager.domains.repository.SslCertificateRepository;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.service.ServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProvisioningRunner {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningRunner.class);

    private final ProvisioningJobRepository jobRepository;
    private final DomainAssignmentRepository assignmentRepository;
    private final SslCertificateRepository sslCertificateRepository;
    private final AcmeService acmeService;
    private final NginxConfigService nginxConfigService;
    private final SslVerificationService verificationService;
    private final ServerService serverService;
    private final AuditService auditService;
    private final SslConfig sslConfig;

    public ProvisioningRunner(ProvisioningJobRepository jobRepository,
                              DomainAssignmentRepository assignmentRepository,
                              SslCertificateRepository sslCertificateRepository,
                              AcmeService acmeService,
                              NginxConfigService nginxConfigService,
                              SslVerificationService verificationService,
                              ServerService serverService,
                              AuditService auditService,
                              SslConfig sslConfig) {
        this.jobRepository = jobRepository;
        this.assignmentRepository = assignmentRepository;
        this.sslCertificateRepository = sslCertificateRepository;
        this.acmeService = acmeService;
        this.nginxConfigService = nginxConfigService;
        this.verificationService = verificationService;
        this.serverService = serverService;
        this.auditService = auditService;
        this.sslConfig = sslConfig;
    }

    @Async("provisioningExecutor")
    public void run(UUID jobId) {
        // Retry the initial lookup to tolerate the async-vs-transaction visibility race: if
        // the caller's @Transactional hasn't committed yet, the row is invisible to a fresh
        // session. Orchestrators should defer dispatch via afterCommit but this is belt-and-
        // suspenders so a stuck job never means "silent" failure.
        ProvisioningJob job = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            job = jobRepository.findById(jobId).orElse(null);
            if (job != null) break;
            try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
        }
        if (job == null) {
            // Row either doesn't exist OR was INSERTed by a tx that has yet to commit.
            // Mark it FAILED directly via UPDATE so operators see a failure instead of a
            // phantom RUNNING row.
            log.error("Provisioning job {} not visible after 3 retries — giving up and marking FAILED", jobId);
            try { jobRepository.markFailedById(jobId, "Runner could not load job row (tx visibility)"); } catch (Exception ignored) { }
            return;
        }
        if (job.getStatus() == ProvisioningJobStatus.CANCELLED) {
            return;
        }

        DomainAssignment assignment = assignmentRepository.findById(job.getDomainAssignmentId()).orElse(null);
        if (assignment == null) {
            failJob(job, ProvisioningStep.FAILED_PERMANENT, "Domain assignment not found");
            return;
        }

        Server server;
        try {
            server = serverService.getServerEntity(job.getServerId());
        } catch (Exception e) {
            failJob(job, ProvisioningStep.FAILED_PERMANENT, "Server not found: " + e.getMessage());
            return;
        }

        String hostname = assignment.getHostname();
        String email = sslConfig.getAdminEmail();

        job.setStartedAt(Instant.now());
        job.appendLog("Starting SSL provisioning for " + hostname);

        try {
            // Step 1: Ensure prerequisites installed (distro-aware). Returns an InstallOutcome
            // that tells us whether host nginx is managed (port 80 was free) or co-existing
            // with another process (port 80 held by docker-proxy / traefik / caddy / etc.).
            if (isCancelled(job)) return;
            updateStep(job, ProvisioningStep.PENDING_DNS, "Ensuring nginx + certbot are installed");
            AcmeService.InstallOutcome install;
            try {
                install = acmeService.ensureNginxAndCertbot(server);
            } catch (Exception installEx) {
                failJob(job, ProvisioningStep.FAILED_RETRYABLE,
                        "Install step failed: " + installEx.getMessage());
                return;
            }
            if (install.rawLog() != null && !install.rawLog().isBlank()) {
                job.appendLog(install.rawLog());
                jobRepository.save(job);
            }

            // Step 2: Run certbot DNS-01 (handles TXT record creation, propagation, and cert issuance)
            if (isCancelled(job)) return;
            updateStep(job, ProvisioningStep.DNS_CREATED, "Running certbot DNS-01 challenge (creates TXT, waits for propagation, issues cert)");
            AcmeService.CertbotResult certbotResult = acmeService.runCertbotWithDns01(server, assignment, hostname, email);

            // Store TXT record ID for cleanup tracking
            if (certbotResult.txtRecordId() != null) {
                job.setAcmeTxtRecordId(certbotResult.txtRecordId());
            }
            if (certbotResult.output() != null && !certbotResult.output().isBlank()) {
                job.appendLog("Certbot output:\n" + certbotResult.output());
            }
            jobRepository.save(job);

            if (!certbotResult.success()) {
                String error = certbotResult.error() != null ? certbotResult.error() : certbotResult.output();
                boolean retryable = error != null && !error.contains("rate limit") && !error.contains("too many");
                failJob(job, retryable ? ProvisioningStep.FAILED_RETRYABLE : ProvisioningStep.FAILED_PERMANENT,
                        "Certbot failed: " + (error != null ? error.strip() : "unknown error"));
                return;
            }

            updateStep(job, ProvisioningStep.CERT_ISSUED, "Certificate issued successfully");

            // Figure out target port from any pre-existing cert (user may have set one).
            int targetPort = sslConfig.getTargetPort();
            Optional<SslCertificate> existingCert = sslCertificateRepository.findByAssignmentId(assignment.getId());
            if (existingCert.isPresent() && existingCert.get().getTargetPort() > 0) {
                targetPort = existingCert.get().getTargetPort();
            }

            Instant certExpiry = null;
            if (install.hostNginxManaged()) {
                // Step 3a: Deploy nginx config (only when we manage nginx).
                if (isCancelled(job)) return;
                updateStep(job, ProvisioningStep.DEPLOYING_CONFIG, "Deploying nginx HTTPS config");
                nginxConfigService.ensureManagedDirectory(server);
                nginxConfigService.deployConfig(server, hostname, assignment.getId().toString(), targetPort);

                try {
                    nginxConfigService.testAndReload(server);
                } catch (Exception e) {
                    nginxConfigService.removeConfig(server, hostname);
                    try { nginxConfigService.testAndReload(server); } catch (Exception ignored) { }
                    failJob(job, ProvisioningStep.FAILED_PERMANENT, "Nginx config failed: " + e.getMessage());
                    return;
                }

                // Step 4a: Verify HTTPS — only meaningful when we own port 443.
                if (isCancelled(job)) return;
                updateStep(job, ProvisioningStep.VERIFYING, "Verifying HTTPS endpoint and TLS certificate");
                SslVerificationService.VerificationResult verification = verificationService.verify(server, hostname);
                job.appendLog("Verification: HTTPS=%s, TLS=%s, expiry=%s".formatted(
                        verification.httpsReachable(), verification.tlsValid(), verification.certExpiry()));
                certExpiry = verification.certExpiry();
            } else {
                // Co-existence mode: host nginx is NOT managed by us. Skip DEPLOYING_CONFIG
                // and VERIFYING entirely — the cert is on disk, the user's reverse proxy
                // will pick it up. Use certbot's default 90-day validity for expiresAt.
                job.appendLog(("Host nginx not managed (port 80 held by '%s'). "
                        + "Certificate is on disk at /etc/letsencrypt/live/%s/. "
                        + "Mount fullchain.pem + privkey.pem into your reverse proxy "
                        + "(nginx / traefik / caddy / docker-proxy).")
                        .formatted(install.portHolderName(), hostname));
            }

            // Step 5: Complete
            updateStep(job, ProvisioningStep.COMPLETED, "SSL provisioning completed successfully");
            SslCertificate cert = existingCert.orElseGet(SslCertificate::new);
            cert.setServerId(job.getServerId());
            cert.setAssignmentId(assignment.getId());
            cert.setHostname(hostname);
            cert.setStatus(SslStatus.ACTIVE);
            cert.setAdminEmail(email);
            cert.setTargetPort(targetPort);
            cert.setExpiresAt(certExpiry != null
                    ? certExpiry
                    : Instant.now().plus(90, ChronoUnit.DAYS));
            cert.setLastRenewedAt(Instant.now());
            cert.setLastError(null);
            cert.setProvisioningJobId(job.getId());
            cert.setHostNginxManaged(install.hostNginxManaged());
            sslCertificateRepository.save(cert);

            try {
                serverService.updateSslEnabled(server.getId(), true);
            } catch (Exception ignored) { }

            job.setStatus(ProvisioningJobStatus.COMPLETED);
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);

            try {
                auditService.log(AuditAction.SSL_PROVISIONED, "SSL_CERTIFICATE", cert.getId(),
                        job.getTriggeredBy(),
                        "SSL provisioned for %s on server '%s' via DNS-01".formatted(hostname, server.getName()));
            } catch (Exception ignored) { }

            log.info("SSL provisioning completed for {} on server '{}'", hostname, server.getName());

        } catch (Exception e) {
            failJob(job, ProvisioningStep.FAILED_RETRYABLE, "Unexpected error: " + e.getMessage());
            log.error("SSL provisioning failed for {} on server '{}': {}",
                    hostname, server.getName(), e.getMessage(), e);
        } finally {
            // Always clean up TXT record
            if (job.getAcmeTxtRecordId() != null && assignment != null) {
                try {
                    acmeService.deleteAcmeChallengeTxtRecord(assignment, job.getAcmeTxtRecordId());
                    job.appendLog("Cleaned up ACME TXT record");
                    jobRepository.save(job);
                } catch (Exception e) {
                    log.warn("Failed to clean up ACME TXT record for job {}: {}", jobId, e.getMessage());
                }
            }
        }
    }

    private void updateStep(ProvisioningJob job, ProvisioningStep step, String message) {
        job.setCurrentStep(step);
        job.appendLog("[%s] %s".formatted(step.name(), message));
        jobRepository.save(job);
        log.info("Provisioning job {} step {}: {}", job.getId(), step, message);
    }

    private boolean isCancelled(ProvisioningJob job) {
        return jobRepository.findById(job.getId())
                .map(j -> j.getStatus() == ProvisioningJobStatus.CANCELLED)
                .orElse(true);
    }

    private void failJob(ProvisioningJob job, ProvisioningStep failStep, String errorMessage) {
        job.setCurrentStep(failStep);
        job.setStatus(ProvisioningJobStatus.FAILED);
        job.setErrorMessage(errorMessage);
        job.setFinishedAt(Instant.now());
        job.appendLog("[FAILED] " + errorMessage);
        jobRepository.save(job);
        log.error("Provisioning job {} failed at step {}: {}", job.getId(), failStep, errorMessage);
    }
}
