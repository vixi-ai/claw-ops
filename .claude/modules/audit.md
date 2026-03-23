# Audit Module

## Purpose

Tracks all critical operations across the platform. Provides an immutable log of user actions for security analysis, debugging, and compliance. Audit logs are append-only — they cannot be modified or deleted.

## Package

`com.openclaw.manager.openclawserversmanager.audit`

## Components

### Entity: `AuditLog`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| userId | UUID | FK → User, nullable (system events) |
| action | AuditAction (enum) | NOT NULL |
| entityType | String | NOT NULL (e.g., "SERVER", "USER", "DEPLOYMENT") |
| entityId | UUID | nullable (the affected entity's ID) |
| details | String (TEXT) | nullable (JSON with extra context) |
| ipAddress | String | nullable (client IP) |
| createdAt | Instant | auto-set, NOT NULL |

### Enum: `AuditAction`

- `USER_LOGIN`
- `USER_LOGIN_FAILED`
- `USER_LOGOUT`
- `USER_CREATED`
- `USER_UPDATED`
- `USER_DISABLED`
- `SERVER_CREATED`
- `SERVER_UPDATED`
- `SERVER_DELETED`
- `SERVER_CONNECTION_TESTED`
- `SECRET_CREATED`
- `SECRET_UPDATED`
- `SECRET_DELETED`
- `SSH_COMMAND_EXECUTED`
- `TERMINAL_SESSION_OPENED`
- `TERMINAL_SESSION_CLOSED`
- `DEPLOYMENT_STARTED`
- `DEPLOYMENT_COMPLETED`
- `DEPLOYMENT_FAILED`
- `TEMPLATE_CREATED`
- `TEMPLATE_DEPLOYED`
- `DOMAIN_PROVISIONED`
- `DOMAIN_SSL_ISSUED`
- `DOMAIN_DELETED`

### DTOs

**`AuditLogResponse`**
- `id`, `userId`, `action`, `entityType`, `entityId`, `details`, `ipAddress`, `createdAt`

**`AuditLogFilter`** (query params)
- `userId` — filter by user
- `action` — filter by action type
- `entityType` — filter by entity type
- `entityId` — filter by specific entity
- `from` — start date
- `to` — end date

### Service: `AuditService`

- `log(AuditAction action, String entityType, UUID entityId, UUID userId, String details)` — creates audit entry
- `log(AuditAction action, String entityType, UUID entityId, UUID userId)` — without details
- `getLogs(AuditLogFilter, Pageable)` → paginated, filtered list
- `getLogsForEntity(String entityType, UUID entityId, Pageable)` → logs for a specific entity

**Usage pattern in other modules:**
```java
auditService.log(AuditAction.SERVER_CREATED, "SERVER", server.getId(), currentUserId,
    "Server '%s' registered".formatted(server.getName()));
```

### Repository: `AuditLogRepository`

- Custom query methods for filtered searches with `Specification<AuditLog>` or `@Query`
- `findByUserId(UUID, Pageable)` → `Page<AuditLog>`
- `findByEntityTypeAndEntityId(String, UUID, Pageable)` → `Page<AuditLog>`
- `findByAction(AuditAction, Pageable)` → `Page<AuditLog>`

## API Endpoints

| Method | Path | Auth | Role | Description |
|--------|------|------|------|-------------|
| GET | `/api/v1/audit/logs` | Yes | ADMIN | Query audit logs (paginated, filtered) |

## Business Rules

- Audit logs are **append-only** — no update or delete operations exist
- The audit API endpoint is read-only and restricted to ADMIN role
- `AuditService.log()` calls should **never throw exceptions** that disrupt the calling operation — use try/catch internally and log failures to application logs
- The `details` field stores JSON for structured context (e.g., changed fields, command text, error messages)
- Audit logging should be lightweight — consider async writing if performance becomes a concern
- IP address should be captured from the HTTP request where available

## Security Considerations

- Audit logs must never contain sensitive data (passwords, private keys, decrypted secrets)
- The `details` field should sanitize/redact sensitive content before storage
- Only ADMIN role can view audit logs
- Consider log retention policies for long-running production deployments

## Dependencies

- None — the audit module is a dependency for all other modules, not the other way around
- It should have zero dependencies on other modules to avoid circular references
