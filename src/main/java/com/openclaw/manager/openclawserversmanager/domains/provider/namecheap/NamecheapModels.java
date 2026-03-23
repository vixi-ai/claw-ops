package com.openclaw.manager.openclawserversmanager.domains.provider.namecheap;

import java.util.List;

public final class NamecheapModels {

    private NamecheapModels() {
    }

    /**
     * Parsed API response wrapper.
     */
    public record ApiResponse(
            String status,
            List<ApiError> errors,
            Object commandResponse
    ) {
        public boolean isOk() {
            return "OK".equalsIgnoreCase(status);
        }
    }

    public record ApiError(
            int number,
            String message
    ) {
    }

    /**
     * A single DNS host record from getHosts response.
     */
    public record HostRecord(
            String hostId,
            String name,
            String type,
            String address,
            int mxPref,
            int ttl
    ) {
    }

    /**
     * Result of getHosts call.
     */
    public record GetHostsResult(
            String domain,
            boolean isUsingOurDns,
            List<HostRecord> hosts
    ) {
    }

    /**
     * Splits "example.com" into SLD="example" and TLD="com".
     * Handles multi-part TLDs like "co.uk" by splitting on the first dot.
     */
    public record DomainParts(String sld, String tld) {

        public static DomainParts from(String zoneName) {
            String name = zoneName.toLowerCase().trim();
            if (name.endsWith(".")) {
                name = name.substring(0, name.length() - 1);
            }
            int dotIndex = name.indexOf('.');
            if (dotIndex < 0) {
                throw new IllegalArgumentException("Invalid domain name: " + zoneName);
            }
            return new DomainParts(name.substring(0, dotIndex), name.substring(dotIndex + 1));
        }
    }
}
