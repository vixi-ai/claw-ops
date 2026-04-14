package com.openclaw.manager.openclawserversmanager.domains.service;

import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.ssh.model.CommandResult;
import com.openclaw.manager.openclawserversmanager.ssh.service.SshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.openclaw.manager.openclawserversmanager.common.validation.HostnameValidator;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SslVerificationService {

    private static final Logger log = LoggerFactory.getLogger(SslVerificationService.class);
    private static final Pattern EXPIRY_PATTERN = Pattern.compile(
            "notAfter=(.+)"
    );
    private static final DateTimeFormatter OPENSSL_DATE_FORMAT = DateTimeFormatter.ofPattern(
            "MMM ppd HH:mm:ss yyyy z", Locale.US
    );

    private final SshService sshService;

    public SslVerificationService(SshService sshService) {
        this.sshService = sshService;
    }

    public ProbeResult probe(Server server, String hostname) {
        HostnameValidator.requireValid(hostname);
        String httpCode = fetchHttpStatus(server,
                "curl -sI -o /dev/null -w '%%{http_code}' --max-time 10 http://%s || echo 'FAILED'"
                        .formatted(hostname));
        String httpsCode = fetchHttpStatus(server,
                "curl -skI -o /dev/null -w '%%{http_code}' --max-time 10 https://%s || echo 'FAILED'"
                        .formatted(hostname));
        Instant certExpiry = verifyTlsCertificate(server, hostname);

        boolean httpReachable = isReachableStatus(httpCode);
        boolean httpsReachable = isReachableStatus(httpsCode);
        boolean tlsPresent = certExpiry != null;
        boolean tlsValid = certExpiry != null && certExpiry.isAfter(Instant.now());

        log.info("Domain probe for {}: http={}, https={}, tlsPresent={}, tlsValid={}",
                hostname, httpCode, httpsCode, tlsPresent, tlsValid);

        return new ProbeResult(
                httpCode,
                httpReachable,
                httpsCode,
                httpsReachable,
                certExpiry,
                tlsPresent,
                tlsValid
        );
    }

    /**
     * Checks if HTTPS endpoint is reachable from the server via curl.
     */
    public boolean verifyHttpsReachable(Server server, String hostname) {
        ProbeResult probe = probe(server, hostname);
        boolean reachable = probe.httpsReachable();
        log.info("HTTPS check for {}: HTTP {} (reachable={})", hostname, probe.httpsCode(), reachable);
        return reachable;
    }

    /**
     * Checks TLS certificate validity via openssl. Returns cert expiry or null.
     */
    public Instant verifyTlsCertificate(Server server, String hostname) {
        CommandResult result = sshService.executeCommand(server,
                "echo | openssl s_client -connect %s:443 -servername %s 2>/dev/null | openssl x509 -noout -enddate 2>/dev/null"
                        .formatted(hostname, hostname), 20);

        if (result.exitCode() != 0 || result.stdout().isBlank()) {
            log.warn("TLS verification failed for {}: no certificate found", hostname);
            return null;
        }

        // Parse "notAfter=Mar 24 12:00:00 2026 GMT" or similar
        String out = result.stdout().trim();
        try {
            Matcher matcher = EXPIRY_PATTERN.matcher(out);
            if (matcher.find()) {
                String dateStr = matcher.group(1).trim();
                try {
                    // Primary: parse openssl's actual format "Mar 24 12:00:00 2026 GMT"
                    ZonedDateTime zdt = ZonedDateTime.parse(dateStr, OPENSSL_DATE_FORMAT);
                    return zdt.toInstant();
                } catch (Exception e1) {
                    try {
                        // Fallback: ISO date "2026-03-24"
                        if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                            return Instant.parse(dateStr.substring(0, 10) + "T00:00:00Z");
                        }
                    } catch (Exception e2) {
                        log.warn("Failed to parse TLS cert expiry for {}: {}", hostname, dateStr);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse TLS cert expiry for {}: {}", hostname, e.getMessage());
        }

        return null;
    }

    /**
     * Full verification: HTTPS reachability + TLS certificate validity.
     */
    public VerificationResult verify(Server server, String hostname) {
        ProbeResult probe = probe(server, hostname);
        boolean httpsReachable = probe.httpsReachable();
        Instant certExpiry = probe.certExpiry();
        boolean tlsValid = probe.tlsValid();

        return new VerificationResult(httpsReachable, tlsValid, certExpiry,
                httpsReachable ? null : "HTTPS endpoint not reachable");
    }

    private String fetchHttpStatus(Server server, String command) {
        CommandResult result = sshService.executeCommand(server, command, 20);
        return result.stdout() != null ? result.stdout().trim() : "";
    }

    private boolean isReachableStatus(String code) {
        return code != null && (code.startsWith("2")
                || code.startsWith("3")
                || code.equals("401")
                || code.equals("403")
                || code.equals("404"));
    }

    public record ProbeResult(
            String httpCode,
            boolean httpReachable,
            String httpsCode,
            boolean httpsReachable,
            Instant certExpiry,
            boolean tlsPresent,
            boolean tlsValid
    ) {
        public static ProbeResult empty() {
            return new ProbeResult("", false, "", false, null, false, false);
        }

        public boolean anyReachable() {
            return httpReachable || httpsReachable;
        }
    }

    public record VerificationResult(
            boolean httpsReachable,
            boolean tlsValid,
            Instant certExpiry,
            String errorDetail
    ) {}
}
