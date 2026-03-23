package com.openclaw.manager.openclawserversmanager.domains.provider;

import com.openclaw.manager.openclawserversmanager.domains.entity.DnsRecordType;

public record DnsRecord(
        String providerRecordId,
        String hostname,
        DnsRecordType type,
        String value,
        int ttl,
        boolean proxied
) {
}
