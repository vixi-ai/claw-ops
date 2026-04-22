package com.openclaw.manager.openclawserversmanager.audit.dto;

import java.time.Instant;

public record DeleteAuditLogsResponse(
        long deletedCount,
        Instant before
) {
}
