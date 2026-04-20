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
            // Step 1: Ensure nginx installed
            if (isCancelled(job)) return;
            updateStep(job, ProvisioningStep.PENDING_DNS, "Ensuring nginx + certbot are installed");
            acmeService.ensureNginxInstalled(server);

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

            // Step 3: Deploy nginx config
            if (isCancelled(job)) return;
            updateStep(job, ProvisioningStep.DEPLOYING_CONFIG, "Deploying nginx HTTPS config");
            nginxConfigService.ensureManagedDirectory(server);
            int targetPort = sslConfig.getTargetPort();
            Optional<SslCertificate> existingCert = sslCertificateRepository.findByAssignmentId(assignment.getId());
            if (existingCert.isPresent() && existingCert.get().getTargetPort() > 0) {
                targetPort = existingCert.get().getTargetPort();
            }
            nginxConfigService.deployConfig(server, hostname, assignment.getId().toString(), targetPort);

            // Test and reload nginx
            try {
                nginxConfigService.testAndReload(server);
            } catch (Exception e) {
                nginxConfigService.removeConfig(server, hostname);
                try { nginxConfigService.testAndReload(server); } catch (Exception ignored) { }
                failJob(job, ProvisioningStep.FAILED_PERMANENT, "Nginx config failed: " + e.getMessage());
                return;
            }

            // Step 4: Verify HTTPS
            if (isCancelled(job)) return;
            updateStep(job, ProvisioningStep.VERIFYING, "Verifying HTTPS endpoint and TLS certificate");
            SslVerificationService.VerificationResult verification = verificationService.verify(server, hostname);
            job.appendLog("Verification: HTTPS=%s, TLS=%s, expiry=%s".formatted(
                    verification.httpsReachable(), verification.tlsValid(), verification.certExpiry()));

            // Step 5: Complete
            updateStep(job, ProvisioningStep.COMPLETED, "SSL provisioning completed successfully");
            SslCertificate cert = existingCert.orElseGet(SslCertificate::new);
            cert.setServerId(job.getServerId());
            cert.setAssignmentId(assignment.getId());
            cert.setHostname(hostname);
            cert.setStatus(SslStatus.ACTIVE);
            cert.setAdminEmail(email);
            cert.setTargetPort(targetPort);
            cert.setExpiresAt(verification.certExpiry() != null
                    ? verification.certExpiry()
                    : Instant.now().plus(90, ChronoUnit.DAYS));
            cert.setLastRenewedAt(Instant.now());
            cert.setLastError(null);
            cert.setProvisioningJobId(job.getId());
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
