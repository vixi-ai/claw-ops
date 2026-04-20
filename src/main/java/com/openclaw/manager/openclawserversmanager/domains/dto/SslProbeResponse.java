package com.openclaw.manager.openclawserversmanager.domains.dto;

import java.time.Instant;

/**
 * Live-wire probe of an SSL-protected endpoint. Populated by {@code SslVerificationService.probe()}
 * — tells the user what the world sees right now, as opposed to whatever the DB says.
 */
public record SslProbeResponse(
        String hostname,
        String httpCode,
        boolean httpReachable,
        String httpsCode,
        boolean httpsReachable,
        Instant certExpiry,
        boolean tlsPresent,
        boolean tlsValid,
        Instant probedAt
) {
}
