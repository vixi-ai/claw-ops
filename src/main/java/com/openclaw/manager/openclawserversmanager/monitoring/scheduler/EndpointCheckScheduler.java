package com.openclaw.manager.openclawserversmanager.monitoring.scheduler;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.EndpointCheck;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.EndpointCheckResult;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.EndpointCheckRepository;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.EndpointCheckResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLSession;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schedules and executes endpoint checks (HTTP, HTTPS, TCP, SSL_CERT, DNS).
 * Runs every 30s, selects checks due based on their intervalSeconds.
 */
@Component
public class EndpointCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(EndpointCheckScheduler.class);

    private final EndpointCheckRepository checkRepository;
    private final EndpointCheckResultRepository resultRepository;
    private final HttpClient httpClient;

    // Track last check time per endpoint to determine when due
    private final Map<java.util.UUID, Instant> lastChecked = new ConcurrentHashMap<>();

    public EndpointCheckScheduler(EndpointCheckRepository checkRepository,
                                   EndpointCheckResultRepository resultRepository) {
        this.checkRepository = checkRepository;
        this.resultRepository = resultRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Scheduled(fixedDelay = 30000)
    public void tick() {
        List<EndpointCheck> enabledChecks = checkRepository.findByEnabled(true);
        if (enabledChecks.isEmpty()) return;

        Instant now = Instant.now();
        int executed = 0;

        for (EndpointCheck check : enabledChecks) {
            Instant last = lastChecked.get(check.getId());
            if (last != null && last.plusSeconds(check.getIntervalSeconds()).isAfter(now)) {
                continue; // Not due yet
            }

            try {
                EndpointCheckResult result = executeCheck(check, now);
                resultRepository.save(result);
                lastChecked.put(check.getId(), now);
                executed++;
            } catch (Exception e) {
                log.error("Endpoint check '{}' failed unexpectedly: {}", check.getName(), e.getMessage());
            }
        }

        if (executed > 0) {
            log.debug("Endpoint checks executed: {}/{}", executed, enabledChecks.size());
        }
    }

    public EndpointCheckResult executeCheck(EndpointCheck check, Instant now) {
        EndpointCheckResult result = new EndpointCheckResult();
        result.setEndpointCheckId(check.getId());
        result.setCheckedAt(now);

        long start = System.currentTimeMillis();
        try {
            switch (check.getCheckType()) {
                case HTTP, HTTPS -> executeHttpCheck(check, result, start);
                case TCP -> executeTcpCheck(check, result, start);
                case SSL_CERT -> executeSslCertCheck(check, result, start);
                case DNS -> executeDnsCheck(check, result, start);
            }
        } catch (Exception e) {
            result.setUp(false);
            result.setErrorMessage(e.getMessage());
            result.setResponseTimeMs(System.currentTimeMillis() - start);
        }

        return result;
    }

    private void executeHttpCheck(EndpointCheck check, EndpointCheckResult result, long start) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(check.getUrl()))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - start;

        result.setResponseTimeMs(elapsed);
        result.setStatusCode(response.statusCode());

        int expected = check.getExpectedStatusCode() != null ? check.getExpectedStatusCode() : 200;
        result.setUp(response.statusCode() == expected);

        if (!result.isUp()) {
            result.setErrorMessage("Expected status " + expected + " but got " + response.statusCode());
        }

        // Extract SSL info if HTTPS
        Optional<SSLSession> sslSession = response.sslSession();
        if (sslSession.isPresent()) {
            extractSslInfo(sslSession.get(), result);
        }
    }

    private void executeTcpCheck(EndpointCheck check, EndpointCheckResult result, long start) throws Exception {
        URI uri = URI.create(check.getUrl().startsWith("tcp://") ? check.getUrl() : "tcp://" + check.getUrl());
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 80;

        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 10000);
            long elapsed = System.currentTimeMillis() - start;
            result.setResponseTimeMs(elapsed);
            result.setUp(true);
        } catch (Exception e) {
            result.setResponseTimeMs(System.currentTimeMillis() - start);
            result.setUp(false);
            result.setErrorMessage("TCP connect failed: " + e.getMessage());
        }
    }

    private void executeSslCertCheck(EndpointCheck check, EndpointCheckResult result, long start) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(check.getUrl()))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        result.setResponseTimeMs(System.currentTimeMillis() - start);
        result.setStatusCode(response.statusCode());

        Optional<SSLSession> sslSession = response.sslSession();
        if (sslSession.isPresent()) {
            extractSslInfo(sslSession.get(), result);
            result.setUp(result.getSslDaysRemaining() != null && result.getSslDaysRemaining() > 0);
        } else {
            result.setUp(false);
            result.setErrorMessage("No SSL session available");
        }
    }

    private void executeDnsCheck(EndpointCheck check, EndpointCheckResult result, long start) throws Exception {
        // Extract hostname from URL
        String hostname = check.getUrl().replaceAll("^https?://", "").replaceAll("/.*$", "");
        try {
            InetAddress[] addresses = InetAddress.getAllByName(hostname);
            long elapsed = System.currentTimeMillis() - start;
            result.setResponseTimeMs(elapsed);
            result.setUp(addresses.length > 0);
            if (addresses.length == 0) {
                result.setErrorMessage("DNS resolution returned no addresses");
            }
        } catch (Exception e) {
            result.setResponseTimeMs(System.currentTimeMillis() - start);
            result.setUp(false);
            result.setErrorMessage("DNS resolution failed: " + e.getMessage());
        }
    }

    private void extractSslInfo(SSLSession session, EndpointCheckResult result) {
        try {
            java.security.cert.Certificate[] certs = session.getPeerCertificates();
            if (certs.length > 0 && certs[0] instanceof X509Certificate x509) {
                Instant expiry = x509.getNotAfter().toInstant();
                result.setSslExpiresAt(expiry);
                long daysRemaining = Instant.now().until(expiry, ChronoUnit.DAYS);
                result.setSslDaysRemaining((int) daysRemaining);
            }
        } catch (Exception e) {
            log.debug("Could not extract SSL info: {}", e.getMessage());
        }
    }
}
