package com.openclaw.manager.openclawserversmanager.domains.dto;

import java.time.Instant;
import java.util.List;

public record SslDashboardResponse(
        long totalCertificates,
        long activeCertificates,
        long expiredCertificates,
        long expiringSoonCertificates,
        long failedCertificates,
        long provisioningCertificates,
        List<ExpiringSoonEntry> expiringSoon,
        List<RecentFailure> recentFailures
) {
    public record ExpiringSoonEntry(
            String hostname,
            Instant expiresAt,
            long daysUntilExpiry
    ) {}

    public record RecentFailure(
            String hostname,
            String lastError
    ) {}
}
