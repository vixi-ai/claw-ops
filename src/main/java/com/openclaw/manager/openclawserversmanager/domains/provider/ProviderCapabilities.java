package com.openclaw.manager.openclawserversmanager.domains.provider;

import com.openclaw.manager.openclawserversmanager.domains.entity.DnsRecordType;

import java.util.List;

public record ProviderCapabilities(
        boolean supportsBatchOperations,
        boolean supportsProxiedRecords,
        boolean requiresNameserverOwnership,
        int rateLimitPerMinute,
        List<DnsRecordType> supportedRecordTypes
) {
}
