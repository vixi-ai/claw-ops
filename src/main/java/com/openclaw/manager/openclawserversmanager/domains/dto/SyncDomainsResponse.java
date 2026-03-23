package com.openclaw.manager.openclawserversmanager.domains.dto;

public record SyncDomainsResponse(
        int total,
        int imported,
        int skipped
) {
}
