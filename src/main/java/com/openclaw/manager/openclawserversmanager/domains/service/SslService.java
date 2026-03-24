package com.openclaw.manager.openclawserversmanager.domains.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.domains.dto.SslCertificateResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.SslCertificate;
import com.openclaw.manager.openclawserversmanager.domains.entity.SslStatus;
import com.openclaw.manager.openclawserversmanager.domains.exception.DomainException;
import com.openclaw.manager.openclawserversmanager.domains.mapper.SslCertificateMapper;
import com.openclaw.manager.openclawserversmanager.domains.repository.SslCertificateRepository;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.service.ServerService;
import com.openclaw.manager.openclawserversmanager.ssh.model.CommandResult;
import com.openclaw.manager.openclawserversmanager.ssh.service.SshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SSL certificate query and lifecycle service.
 * Provisioning is handled by ProvisioningOrchestrator + ProvisioningRunner.
 * This service handles reads, renewals, removals, and status checks.
 */
@Service
public class SslService {

    private static final Logger log = LoggerFactory.getLogger(SslService.class);
    private static final int SSH_TIMEOUT = 300;
    private static final Pattern EXPIRY_PATTERN = Pattern.compile(
            "Expiry Date:\\s*(\\d{4}-\\d{2}-\\d{2})"
    );

    private final SslCertificateRepository sslCertificateRepository;
    private final ServerService serverService;
    private final SshService sshService;
    private final AuditService auditService;
    private final NginxConfigService nginxConfigService;
    private final AcmeService acmeService;

    public SslService(SslCertificateRepository sslCertificateRepository,
                      ServerService serverService,
                      SshService sshService,
                      AuditService auditService,
                      NginxConfigService nginxConfigService,
                      AcmeService acmeService) {
        this.sslCertificateRepository = sslCertificateRepository;
        this.serverService = serverService;
        this.sshService = sshService;
        this.auditService = auditService;
        this.nginxConfigService = nginxConfigService;
        this.acmeService = acmeService;
    }

    // ── Queries ──────────────────────────────

    public SslCertificateResponse getCertificate(UUID id) {
        return SslCertificateMapper.toResponse(findCertOrThrow(id));
    }

    public Optional<SslCertificateResponse> getCertificateForServer(UUID serverId) {
        return sslCertificateRepository.findByServerId(serverId)
                .map(SslCertificateMapper::toResponse);
    }

    public Page<SslCertificateResponse> getAllCertificates(Pageable pageable) {
        return sslCertificateRepository.findAll(pageable).map(SslCertificateMapper::toResponse);
    }

    // ── Renew ──────────────────────────────

    @Transactional
    public SslCertificateResponse renew(UUID certId, UUID userId) {
        SslCertificate cert = findCertOrThrow(certId);
        Server server = serverService.getServerEntity(cert.getServerId());

        try {
            CommandResult result = sshService.executeCommand(server,
                    "sudo certbot renew --cert-name %s --non-interactive".formatted(cert.getHostname()),
                    SSH_TIMEOUT);

            if (result.exitCode() != 0) {
                cert.setLastError("Renew failed: " + result.stderr());
                sslCertificateRepository.save(cert);
                throw new DomainException("Certbot renew failed: " + result.stderr());
            }

            cert.setExpiresAt(Instant.now().plus(90, ChronoUnit.DAYS));
            cert.setLastRenewedAt(Instant.now());
            cert.setStatus(SslStatus.ACTIVE);
            cert.setLastError(null);
            sslCertificateRepository.save(cert);

            try {
                auditService.log(AuditAction.SSL_RENEWED, "SSL_CERTIFICATE", certId, userId,
                        "SSL renewed for %s".formatted(cert.getHostname()));
            } catch (Exception ignored) { }

        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            cert.setLastError(e.getMessage());
            sslCertificateRepository.save(cert);
            throw new DomainException("SSL renewal failed: " + e.getMessage());
        }

        return SslCertificateMapper.toResponse(cert);
    }

    // ── Remove ──────────────────────────────

    @Transactional
    public void remove(UUID certId, UUID userId) {
        SslCertificate cert = findCertOrThrow(certId);
        String hostname = cert.getHostname();

        cert.setStatus(SslStatus.REMOVING);
        sslCertificateRepository.save(cert);

        if (cert.getServerId() != null) {
            try {
                Server server = serverService.getServerEntity(cert.getServerId());

                // Delete certbot cert
                acmeService.deleteCertbotCert(server, hostname);

                // Remove nginx config from managed directory + graceful reload
                nginxConfigService.removeConfig(server, hostname);
                try {
                    nginxConfigService.testAndReload(server);
                } catch (Exception e) {
                    log.warn("Nginx reload failed after removing config for {}: {}", hostname, e.getMessage());
                }

                // Update server flag
                serverService.updateSslEnabled(cert.getServerId(), false);
            } catch (Exception e) {
                log.warn("SSL cleanup failed for {}: {}", hostname, e.getMessage());
            }
        }

        sslCertificateRepository.delete(cert);

        try {
            auditService.log(AuditAction.SSL_REMOVED, "SSL_CERTIFICATE", certId, userId,
                    "SSL removed for %s".formatted(hostname));
        } catch (Exception ignored) { }
    }

    // ── Check ──────────────────────────────

    @Transactional
    public SslCertificateResponse check(UUID certId, UUID userId) {
        SslCertificate cert = findCertOrThrow(certId);
        Server server = serverService.getServerEntity(cert.getServerId());

        try {
            CommandResult result = sshService.executeCommand(server,
                    "sudo certbot certificates --cert-name %s".formatted(cert.getHostname()), 60);

            if (result.exitCode() == 0) {
                Matcher matcher = EXPIRY_PATTERN.matcher(result.stdout());
                if (matcher.find()) {
                    Instant expiry = Instant.parse(matcher.group(1) + "T00:00:00Z");
                    cert.setExpiresAt(expiry);
                    cert.setStatus(expiry.isBefore(Instant.now()) ? SslStatus.EXPIRED : SslStatus.ACTIVE);
                }
                cert.setLastError(null);
            } else {
                cert.setStatus(SslStatus.FAILED);
                cert.setLastError("Certificate not found by certbot");
            }

            sslCertificateRepository.save(cert);

            try {
                auditService.log(AuditAction.SSL_CHECK, "SSL_CERTIFICATE", certId, userId,
                        "SSL check for %s: %s".formatted(cert.getHostname(), cert.getStatus()));
            } catch (Exception ignored) { }

        } catch (Exception e) {
            cert.setLastError("Check failed: " + e.getMessage());
            sslCertificateRepository.save(cert);
        }

        return SslCertificateMapper.toResponse(cert);
    }

    // ── Remove by server (used during server deletion) ──────────────────────────────

    @Transactional
    public void removeByServerId(UUID serverId, UUID userId) {
        sslCertificateRepository.findByServerId(serverId).ifPresent(cert -> {
            try {
                remove(cert.getId(), userId);
            } catch (Exception e) {
                log.warn("Failed to remove SSL for server {}: {}", serverId, e.getMessage());
                sslCertificateRepository.delete(cert);
            }
        });
    }

    private SslCertificate findCertOrThrow(UUID id) {
        return sslCertificateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SSL certificate with id " + id + " not found"));
    }
}
