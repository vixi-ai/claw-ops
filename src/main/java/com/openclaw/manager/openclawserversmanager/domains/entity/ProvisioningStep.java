package com.openclaw.manager.openclawserversmanager.domains.entity;

public enum ProvisioningStep {
    PENDING_DNS,
    DNS_CREATED,
    DNS_PROPAGATED,
    ISSUING_CERT,
    CERT_ISSUED,
    DEPLOYING_CONFIG,
    VERIFYING,
    COMPLETED,
    FAILED_RETRYABLE,
    FAILED_PERMANENT
}
