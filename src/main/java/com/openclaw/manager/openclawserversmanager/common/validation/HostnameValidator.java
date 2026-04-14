package com.openclaw.manager.openclawserversmanager.common.validation;

import java.util.regex.Pattern;

/**
 * Validates and sanitizes hostnames before use in shell commands to prevent command injection.
 */
public final class HostnameValidator {

    private static final Pattern VALID_HOSTNAME = Pattern.compile(
            "^[a-z0-9]([a-z0-9\\-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9\\-]*[a-z0-9])?)*$"
    );
    private static final int MAX_HOSTNAME_LENGTH = 253;
    private static final int MAX_LABEL_LENGTH = 63;

    private HostnameValidator() {
    }

    /**
     * Returns true if the hostname is safe for use in shell commands and DNS operations.
     */
    public static boolean isValid(String hostname) {
        if (hostname == null || hostname.isBlank()) {
            return false;
        }

        String normalized = hostname.trim().toLowerCase();
        if (normalized.length() > MAX_HOSTNAME_LENGTH) {
            return false;
        }

        if (!VALID_HOSTNAME.matcher(normalized).matches()) {
            return false;
        }

        for (String label : normalized.split("\\.")) {
            if (label.length() > MAX_LABEL_LENGTH) {
                return false;
            }
        }

        return true;
    }

    /**
     * Validates hostname and throws IllegalArgumentException if invalid.
     */
    public static String requireValid(String hostname) {
        if (!isValid(hostname)) {
            throw new IllegalArgumentException(
                    "Invalid hostname '%s': must contain only lowercase alphanumeric characters, hyphens, and dots"
                            .formatted(hostname));
        }
        return hostname.trim().toLowerCase();
    }
}
