package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.EndpointCheck;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.EndpointCheckResult;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.EndpointCheckType;

import java.time.Instant;
import java.util.UUID;

public record EndpointCheckResponse(
        UUID id,
        String name,
        String url,
        EndpointCheckType checkType,
        UUID serverId,
        Integer expectedStatusCode,
        boolean enabled,
        int intervalSeconds,
        LatestResult latestResult,
        Instant createdAt,
        Instant updatedAt
) {
    public record LatestResult(
            boolean isUp,
            Long responseTimeMs,
            Integer statusCode,
            Instant sslExpiresAt,
            Integer sslDaysRemaining,
            String errorMessage,
            Instant checkedAt
    ) {
        public static LatestResult from(EndpointCheckResult r) {
            if (r == null) return null;
            return new LatestResult(r.isUp(), r.getResponseTimeMs(), r.getStatusCode(),
                    r.getSslExpiresAt(), r.getSslDaysRemaining(), r.getErrorMessage(), r.getCheckedAt());
        }
    }

    public static EndpointCheckResponse from(EndpointCheck c, EndpointCheckResult latestResult) {
        return new EndpointCheckResponse(
                c.getId(), c.getName(), c.getUrl(), c.getCheckType(),
                c.getServerId(), c.getExpectedStatusCode(), c.isEnabled(),
                c.getIntervalSeconds(),
                LatestResult.from(latestResult),
                c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
