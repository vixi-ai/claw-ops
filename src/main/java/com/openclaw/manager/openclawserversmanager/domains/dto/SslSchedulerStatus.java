package com.openclaw.manager.openclawserversmanager.domains.dto;

import java.time.Instant;

/**
 * Transparency for the auto-renewal + expiry-marking schedulers. Read-only snapshot of the
 * last run and the next scheduled run derived from the cron expression.
 */
public record SslSchedulerStatus(
        Instant renewLastRunAt,
        Instant renewNextRunAt,
        RenewalOutcome lastOutcome,
        Instant expiryLastRunAt,
        Instant expiryNextRunAt,
        int renewalWindowDays
) {
    public record RenewalOutcome(
            int renewed,
            int failed,
            int considered,
            long durationMs
    ) {
        public static RenewalOutcome empty() {
            return new RenewalOutcome(0, 0, 0, 0L);
        }
    }
}
