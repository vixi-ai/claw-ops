package com.openclaw.manager.openclawserversmanager.domains.scheduler;

import com.openclaw.manager.openclawserversmanager.domains.entity.SslCertificate;
import com.openclaw.manager.openclawserversmanager.domains.entity.SslStatus;
import com.openclaw.manager.openclawserversmanager.domains.repository.SslCertificateRepository;
import com.openclaw.manager.openclawserversmanager.domains.service.AcmeService;
import com.openclaw.manager.openclawserversmanager.domains.service.NginxConfigService;
import com.openclaw.manager.openclawserversmanager.notifications.service.NotificationDispatchService;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.repository.ServerRepository;
import com.openclaw.manager.openclawserversmanager.ssh.model.CommandResult;
import com.openclaw.manager.openclawserversmanager.ssh.service.SshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SslRenewalScheduler {

    private static final Logger log = LoggerFactory.getLogger(SslRenewalScheduler.class);
    private static final int RENEWAL_WINDOW_DAYS = 30;
    private static final int SSH_TIMEOUT = 300;
    private static final Pattern EXPIRY_PATTERN = Pattern.compile(
            "Expiry Date:\\s*(\\d{4}-\\d{2}-\\d{2})"
    );

    private final SslCertificateRepository sslCertificateRepository;
    private final ServerRepository serverRepository;
    private final SshService sshService;
    private final NotificationDispatchService notificationDispatchService;

    public SslRenewalScheduler(SslCertificateRepository sslCertificateRepository,
                               ServerRepository serverRepository,
                               SshService sshService,
                               NotificationDispatchService notificationDispatchService) {
        this.sslCertificateRepository = sslCertificateRepository;
        this.serverRepository = serverRepository;
        this.sshService = sshService;
        this.notificationDispatchService = notificationDispatchService;
    }

    /**
     * Runs daily at 03:00 UTC. Finds certificates expiring within 30 days and attempts renewal.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void renewExpiringCertificates() {
        Instant renewalThreshold = Instant.now().plus(RENEWAL_WINDOW_DAYS, ChronoUnit.DAYS);
        List<SslCertificate> expiring = sslCertificateRepository
                .findByStatusAndExpiresAtBefore(SslStatus.ACTIVE, renewalThreshold);

        if (expiring.isEmpty()) {
            log.debug("No certificates due for renewal");
            return;
        }

        log.info("Found {} certificates due for renewal", expiring.size());
        int renewed = 0;
        int failed = 0;

        for (SslCertificate cert : expiring) {
            if (cert.getServerId() == null) {
                continue;
            }

            Server server = serverRepository.findById(cert.getServerId()).orElse(null);
            if (server == null) {
                log.warn("Server {} not found for cert {} ({})", cert.getServerId(), cert.getId(), cert.getHostname());
                continue;
            }

            try {
                CommandResult result = sshService.executeCommand(server,
                        "sudo certbot renew --cert-name %s --non-interactive".formatted(cert.getHostname()),
                        SSH_TIMEOUT);

                if (result.exitCode() == 0) {
                    Instant newExpiry = parseExpiry(server, cert.getHostname());
                    cert.setExpiresAt(newExpiry != null ? newExpiry : Instant.now().plus(90, ChronoUnit.DAYS));
                    cert.setLastRenewedAt(Instant.now());
                    cert.setStatus(SslStatus.ACTIVE);
                    cert.setLastError(null);
                    sslCertificateRepository.save(cert);
                    renewed++;
                    log.info("Auto-renewed SSL certificate for {}", cert.getHostname());
                } else {
                    cert.setLastError("Auto-renewal failed: " + result.stderr());
                    sslCertificateRepository.save(cert);
                    failed++;
                    log.error("Auto-renewal failed for {}: {}", cert.getHostname(), result.stderr());
                }
            } catch (Exception e) {
                cert.setLastError("Auto-renewal error: " + e.getMessage());
                sslCertificateRepository.save(cert);
                failed++;
                log.error("Auto-renewal error for {}: {}", cert.getHostname(), e.getMessage());
            }
        }

        log.info("SSL auto-renewal complete: {} renewed, {} failed out of {} due", renewed, failed, expiring.size());

        if (failed > 0) {
            try {
                notificationDispatchService.sendToDefault(
                        "SSL Renewal Failures",
                        "%d certificate(s) failed auto-renewal. Check the dashboard for details.".formatted(failed));
            } catch (Exception e) {
                log.warn("Failed to send renewal failure notification: {}", e.getMessage());
            }
        }
    }

    /**
     * Runs daily at 04:00 UTC. Marks expired certificates.
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void markExpiredCertificates() {
        List<SslCertificate> active = sslCertificateRepository.findByStatus(SslStatus.ACTIVE);
        int expired = 0;

        for (SslCertificate cert : active) {
            if (cert.getExpiresAt() != null && cert.getExpiresAt().isBefore(Instant.now())) {
                cert.setStatus(SslStatus.EXPIRED);
                sslCertificateRepository.save(cert);
                expired++;
                log.warn("Certificate for {} has expired", cert.getHostname());
            }
        }

        if (expired > 0) {
            log.warn("Marked {} certificates as expired", expired);
            try {
                notificationDispatchService.sendToDefault(
                        "SSL Certificates Expired",
                        "%d certificate(s) have expired and need immediate attention.".formatted(expired));
            } catch (Exception e) {
                log.warn("Failed to send expiry notification: {}", e.getMessage());
            }
        }
    }

    private Instant parseExpiry(Server server, String hostname) {
        try {
            CommandResult result = sshService.executeCommand(server,
                    "sudo certbot certificates --cert-name %s".formatted(hostname), 60);
            if (result.exitCode() == 0) {
                Matcher matcher = EXPIRY_PATTERN.matcher(result.stdout());
                if (matcher.find()) {
                    return Instant.parse(matcher.group(1) + "T00:00:00Z");
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse cert expiry for {}: {}", hostname, e.getMessage());
        }
        return null;
    }
}
