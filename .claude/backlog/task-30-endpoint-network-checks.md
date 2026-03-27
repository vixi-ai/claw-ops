# Task 30: Endpoint & Network Checks — HTTP, TCP, SSL, Ping

**Status:** TODO
**Module(s):** monitoring
**Priority:** MEDIUM
**Created:** 2026-03-25
**Completed:** —

## Description

Implement external endpoint and network monitoring. Unlike infrastructure metrics (collected via SSH from the server), endpoint checks verify services from the outside — HTTP response codes, TCP port availability, SSL certificate expiration, DNS resolution, and ping latency. These run from the control plane, not via SSH.

## Acceptance Criteria

- [ ] `endpoint_checks` table: per-server endpoint definitions (URL, protocol, expected status, interval)
- [ ] `endpoint_check_results` table: check history (status, response time, error, checked_at)
- [ ] HTTP/HTTPS checks: GET request, verify status code (200/301/302), measure response time
- [ ] TCP port checks: connect to host:port, verify connection succeeds within timeout
- [ ] SSL certificate checks: extract cert expiry, warn at 30 days, critical at 7 days
- [ ] Ping/latency checks: ICMP ping from control plane, measure round-trip time
- [ ] DNS resolution checks: verify hostname resolves to expected IP
- [ ] All checks run from the control plane (Java HttpClient / Socket), NOT via SSH
- [ ] Separate executor: `endpointCheckExecutor` (doesn't compete with SSH-based monitoring)
- [ ] Configurable per-endpoint: interval, timeout, retries, expected status code
- [ ] Endpoint health feeds into overall server health evaluation
- [ ] CRUD endpoints for managing endpoint checks

## Implementation Notes

### EndpointCheck Entity
```java
public class EndpointCheck {
    UUID id;
    UUID serverId;            // nullable — can be standalone endpoint
    EndpointProtocol protocol; // HTTP, HTTPS, TCP, PING, DNS, SSL_CERT
    String target;            // URL, host:port, or hostname
    int expectedStatusCode;   // for HTTP (default 200)
    int timeoutMs;            // default 10000
    int intervalSeconds;      // default 300 (5 min)
    int retryCount;           // default 2
    boolean enabled;
    String currentState;      // HEALTHY, DEGRADED, DOWN, UNKNOWN
    Instant lastCheckedAt;
}
```

### Check Execution (runs on control plane, not SSH)
```java
// HTTP check
HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeout)).build();
HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofMillis(timeout)).build();
long start = System.currentTimeMillis();
HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
long responseTime = System.currentTimeMillis() - start;
boolean healthy = response.statusCode() == expectedStatus;

// SSL cert check
SSLSession session = ...; // from HTTPS connection
X509Certificate cert = (X509Certificate) session.getPeerCertificates()[0];
Instant expiry = cert.getNotAfter().toInstant();
long daysUntilExpiry = ChronoUnit.DAYS.between(Instant.now(), expiry);
```

### Integration with SSL module
- Servers with `sslEnabled=true` and a hostname automatically get an SSL cert expiry check
- Cross-reference with existing `ssl_certificates.expiresAt` for consistency

## Files Modified
<!-- Filled in after completion -->
