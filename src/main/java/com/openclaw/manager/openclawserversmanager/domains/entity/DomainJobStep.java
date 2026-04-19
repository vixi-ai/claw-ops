package com.openclaw.manager.openclawserversmanager.domains.entity;

public enum DomainJobStep {
    PENDING_DNS,
    CREATING_RECORD,
    DNS_CREATED,
    VERIFYING,
    VERIFIED,
    COMPLETED,
    FAILED_RETRYABLE,
    FAILED_PERMANENT
}
