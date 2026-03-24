package com.openclaw.manager.openclawserversmanager.audit.entity;

public enum AuditAction {
    // User lifecycle
    USER_LOGIN,
    USER_LOGIN_FAILED,
    USER_LOGOUT,
    USER_CREATED,
    USER_UPDATED,
    USER_DISABLED,
    USER_DELETED,
    USER_PASSWORD_CHANGED,
    USER_ACCOUNT_LOCKED,

    // Server operations (future)
    SERVER_CREATED,
    SERVER_UPDATED,
    SERVER_DELETED,
    SERVER_CONNECTION_TESTED,

    // Secret operations (future)
    SECRET_CREATED,
    SECRET_UPDATED,
    SECRET_DELETED,

    // SSH operations (future)
    SSH_COMMAND_EXECUTED,

    // Terminal sessions
    TERMINAL_SESSION_REQUESTED,
    TERMINAL_SESSION_OPENED,
    TERMINAL_SESSION_CLOSED,

    // Deployment scripts
    SCRIPT_CREATED,
    SCRIPT_UPDATED,
    SCRIPT_DELETED,

    // Deployment jobs
    JOB_TRIGGERED,
    JOB_CANCELLED,

    // Templates
    TEMPLATE_CREATED,
    TEMPLATE_UPDATED,
    TEMPLATE_DELETED,
    TEMPLATE_DEPLOYED,

    // Domains
    DOMAIN_PROVISIONED,
    DOMAIN_SSL_ISSUED,
    DOMAIN_DELETED,

    // Domain module — provider accounts
    PROVIDER_ACCOUNT_CREATED,
    PROVIDER_ACCOUNT_UPDATED,
    PROVIDER_ACCOUNT_DELETED,

    // Domain module — zones
    ZONE_CREATED,
    ZONE_ACTIVATED,
    ZONE_DELETED,

    // Domain module — assignments
    DOMAIN_ASSIGNED,
    DOMAIN_AUTO_ASSIGNED,
    DOMAIN_RELEASED,
    DOMAIN_VERIFIED,

    // SSL certificates
    SSL_PROVISIONED,
    SSL_RENEWED,
    SSL_REMOVED,
    SSL_CHECK
}
