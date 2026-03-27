# Task 5: Audit Module

**Status:** DONE
**Module(s):** audit
**Priority:** HIGH
**Created:** 2026-03-11
**Completed:** 2026-03-11

## Description
Implement a complete audit logging system that tracks all critical operations across the platform. The audit module is append-only (no updates or deletes), has zero dependencies on other modules, and is designed to be injected into every other module. After building the audit module itself, retrofit audit logging into the existing AuthService and UserService.

## Acceptance Criteria

### Entity & Migration
- [ ] `AuditLog` JPA entity — UUID PK, userId (nullable FK→User), action (enum), entityType (String), entityId (UUID nullable), details (TEXT for JSON), ipAddress (String nullable), createdAt (auto-set)
- [ ] `AuditAction` enum with all 23 action types (see list below)
- [ ] Flyway migration `V4__create_audit_logs_table.sql` with proper indexes

### Repository
- [ ] `AuditLogRepository extends JpaRepository<AuditLog, UUID>` + `JpaSpecificationExecutor<AuditLog>`
- [ ] `findByUserId(UUID, Pageable)` → `Page<AuditLog>`
- [ ] `findByEntityTypeAndEntityId(String, UUID, Pageable)` → `Page<AuditLog>`
- [ ] `findByAction(AuditAction, Pageable)` → `Page<AuditLog>`

### Service
- [ ] `AuditService` with two `log()` overloads:
  - `log(AuditAction action, String entityType, UUID entityId, UUID userId, String details)`
  - `log(AuditAction action, String entityType, UUID entityId, UUID userId)` (no details)
- [ ] `log()` must **NEVER throw exceptions** — wrap in try/catch, log failures to application logger
- [ ] `getLogs(AuditLogFilter filter, Pageable pageable)` → `Page<AuditLogResponse>` with dynamic Specification-based filtering
- [ ] `getLogsForEntity(String entityType, UUID entityId, Pageable pageable)` → `Page<AuditLogResponse>`

### DTOs
- [ ] `AuditLogResponse` — record with all fields: id, userId, action, entityType, entityId, details, ipAddress, createdAt
- [ ] `AuditLogFilter` — record for query params: userId, action, entityType, entityId, from (Instant), to (Instant)

### Specification (Dynamic Filtering)
- [ ] `AuditLogSpecification` class with static methods that build `Specification<AuditLog>` predicates
- [ ] Combine filters dynamically: any combination of userId, action, entityType, entityId, date range
- [ ] All filter params are optional — empty filter returns all logs

### Controller
- [ ] `AuditController` — `GET /api/v1/audit/logs` ADMIN-only, paginated, filtered
- [ ] Accept all filter params as `@RequestParam(required = false)`
- [ ] Accept standard pagination: `page`, `size`, `sort`
- [ ] Swagger `@Tag(name = "Audit")` and `@Operation` annotations

### IP Address Capture
- [ ] `AuditContext` utility to capture client IP from the current HTTP request
- [ ] Use `RequestContextHolder` to get `HttpServletRequest` and extract IP
- [ ] Handle `X-Forwarded-For` header for proxied requests
- [ ] `AuditService.log()` automatically captures IP if called within an HTTP request context

### Security
- [ ] SecurityConfig updated: `/api/v1/audit/**` requires ADMIN role
- [ ] Audit logs must never contain passwords, private keys, or decrypted secrets in the `details` field

### Retrofit Audit into Existing Services
- [ ] **AuthService** — add audit logging for:
  - `USER_LOGIN` — after successful login (include email in details)
  - `USER_LOGIN_FAILED` — after failed login (include email, do NOT include password)
  - `USER_LOGOUT` — after refresh token revocation
- [ ] **UserService** — add audit logging for:
  - `USER_CREATED` — after user creation (include email, username, role)
  - `USER_UPDATED` — after user update (include changed fields)
  - `USER_DISABLED` — after user disable (include username)
  - `USER_DELETED` — after user deletion (include username) — add `USER_DELETED` to enum
- [ ] All audit calls wrapped in try/catch so audit failures never break the primary operation

### Dev Admin Page
- [ ] Update `/dev/audit.html` from placeholder to functional page:
  - Table showing audit logs (id, user, action, entity, details, IP, timestamp)
  - Filter inputs: userId, action dropdown, entityType, date range
  - Pagination controls
  - Auto-refresh toggle

## Implementation Notes

### AuditAction enum — full list
```java
public enum AuditAction {
    // User lifecycle
    USER_LOGIN,
    USER_LOGIN_FAILED,
    USER_LOGOUT,
    USER_CREATED,
    USER_UPDATED,
    USER_DISABLED,
    USER_DELETED,          // ← add this (not in original spec)
    USER_PASSWORD_CHANGED, // ← add this (for change-password tracking)

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

    // Terminal sessions (future)
    TERMINAL_SESSION_OPENED,
    TERMINAL_SESSION_CLOSED,

    // Deployments (future)
    DEPLOYMENT_STARTED,
    DEPLOYMENT_COMPLETED,
    DEPLOYMENT_FAILED,

    // Templates (future)
    TEMPLATE_CREATED,
    TEMPLATE_DEPLOYED,

    // Domains (future)
    DOMAIN_PROVISIONED,
    DOMAIN_SSL_ISSUED,
    DOMAIN_DELETED
}
```

### Migration SQL
```sql
-- V4__create_audit_logs_table.sql
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID,
    details TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
```

Note: `ON DELETE SET NULL` for user_id — if a user is deleted, their audit trail is preserved but userId becomes null.

### AuditContext utility for IP capture
```java
public final class AuditContext {
    private AuditContext() {}

    public static String getCurrentIpAddress() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest request = sra.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            // Not in HTTP context (e.g., scheduled task, bootstrap)
        }
        return null;
    }
}
```

### Specification pattern for dynamic filtering
```java
public final class AuditLogSpecification {
    private AuditLogSpecification() {}

    public static Specification<AuditLog> withFilter(AuditLogFilter filter) {
        return Specification.where(hasUserId(filter.userId()))
            .and(hasAction(filter.action()))
            .and(hasEntityType(filter.entityType()))
            .and(hasEntityId(filter.entityId()))
            .and(createdAfter(filter.from()))
            .and(createdBefore(filter.to()));
    }

    private static Specification<AuditLog> hasUserId(UUID userId) {
        return userId == null ? null : (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }
    // ... similar for each filter field
}
```

### Retrofit pattern for existing services
```java
// In AuthService — after successful login:
auditService.log(AuditAction.USER_LOGIN, "USER", user.getId(), user.getId(),
    "Login from %s".formatted(user.getEmail()));

// In AuthService — after failed login:
auditService.log(AuditAction.USER_LOGIN_FAILED, "USER", null, null,
    "Failed login attempt for %s".formatted(request.email()));

// In UserService — after user creation:
auditService.log(AuditAction.USER_CREATED, "USER", saved.getId(), currentUserId,
    "User '%s' created with role %s".formatted(saved.getUsername(), saved.getRole()));
```

### Recommended implementation order
1. `AuditAction` enum
2. `AuditLog` entity + `V4` migration
3. `AuditLogRepository` (extends JpaRepository + JpaSpecificationExecutor)
4. `AuditContext` utility (IP capture)
5. `AuditLogResponse` + `AuditLogFilter` DTOs
6. `AuditLogSpecification` (dynamic query builder)
7. `AuditService` (log + query methods)
8. `AuditController` + SecurityConfig update
9. Retrofit `AuthService` with audit logging
10. Retrofit `UserService` with audit logging
11. Update `audit.html` dev page
12. Update architecture log

### Package structure
```
com.openclaw.manager.openclawserversmanager/
└── audit/
    ├── controller/AuditController.java
    ├── dto/AuditLogResponse.java
    ├── dto/AuditLogFilter.java
    ├── entity/AuditLog.java
    ├── entity/AuditAction.java
    ├── repository/AuditLogRepository.java
    ├── service/AuditService.java
    ├── specification/AuditLogSpecification.java
    └── util/AuditContext.java
```

## Files Modified

### Audit module (all new)
- `src/main/java/.../audit/entity/AuditAction.java` — 27-value enum
- `src/main/java/.../audit/entity/AuditLog.java` — JPA entity
- `src/main/java/.../audit/repository/AuditLogRepository.java` — JpaRepository + JpaSpecificationExecutor
- `src/main/java/.../audit/util/AuditContext.java` — IP address capture utility
- `src/main/java/.../audit/dto/AuditLogResponse.java` — response DTO
- `src/main/java/.../audit/dto/AuditLogFilter.java` — filter DTO
- `src/main/java/.../audit/specification/AuditLogSpecification.java` — dynamic Specification builder
- `src/main/java/.../audit/service/AuditService.java` — log + query methods
- `src/main/java/.../audit/controller/AuditController.java` — GET /api/v1/audit/logs
- `src/main/resources/db/migration/V4__create_audit_logs_table.sql` — table + indexes

### Modified files
- `src/main/java/.../auth/config/SecurityConfig.java` — added `/api/v1/audit/**` ADMIN rule
- `src/main/java/.../auth/service/AuthService.java` — added audit logging (login, login_failed, logout)
- `src/main/java/.../users/service/UserService.java` — added audit logging (created, updated, disabled, deleted, password_changed)
- `src/main/resources/static/dev/audit.html` — upgraded from placeholder to full audit viewer
- `src/main/resources/static/dev/index.html` — marked Audit as "Active"
