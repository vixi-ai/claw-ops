package com.openclaw.manager.openclawserversmanager.domains.provider;

/**
 * A domain discovered from a provider account.
 */
public record DiscoveredDomain(
        String domainName,
        String providerZoneId,
        boolean manageable
) {
}
