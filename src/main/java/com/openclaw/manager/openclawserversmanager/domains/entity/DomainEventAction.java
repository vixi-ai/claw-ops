package com.openclaw.manager.openclawserversmanager.domains.entity;

public enum DomainEventAction {
    CREDENTIALS_VALIDATED,
    ZONE_VERIFIED,
    RECORD_CREATED,
    RECORD_UPDATED,
    RECORD_DELETED,
    RECORD_VERIFIED,
    ZONE_PREFLIGHT_PASSED,
    ZONE_PREFLIGHT_FAILED
}
