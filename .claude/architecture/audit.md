# Audit — Architecture Log

Living documentation of implemented code. Agents MUST append here after writing code for this module.

## Implemented Components

### AuditAction Enum
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/audit/entity/AuditAction.java`
- **Type:** entity (enum)
- **Description:** 27 action types covering all modules: user lifecycle (8), servers (4), secrets (3), SSH (1), terminal (2), deployments (3), templates (2), domains (3).
- **Dependencies:** None
- **Date:** 2026-03-11

### AuditLog Entity
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/audit/entity/AuditLog.java`
- **Type:** entity
- **Description:** JPA entity for `audit_logs` table. UUID PK, userId (nullable — preserved on user deletion via SET NULL), action enum, entityType string, entityId UUID, details TEXT, ipAddress, createdAt. Append-only design.
- **Dependencies:** AuditAction enum
- **Date:** 2026-03-11

### V4 Migration
- **File(s):** `src/main/resources/db/migration/V4__create_audit_logs_table.sql`
- **Type:** migration
- **Description:** Creates `audit_logs` table with `ON DELETE SET NULL` for user_id FK. Indexes on user_id, action, (entity_type, entity_id), and created_at DESC.
- **Dependencies:** V2 (users table)
- **Date:** 2026-03-11

### AuditLogRepository
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/audit/repository/AuditLogRepository.java`
- **Type:** repository
- **Description:** Extends `JpaRepository` + `JpaSpecificationExecutor`. Methods: `findByUserId`, `findByEntityTypeAndEntityId`, `findByAction` — all paginated.
- **Dependencies:** AuditLog entity
- **Date:** 2026-03-11

### AuditContext Utility
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/audit/util/AuditContext.java`
- **Type:** utility
- **Description:** Static utility to capture client IP from current HTTP request via `RequestContextHolder`. Handles `X-Forwarded-For` for proxied requests. Returns null outside HTTP context.
- **Dependencies:** None
- **Date:** 2026-03-11

### Audit DTOs
- **File(s):**
  - `src/main/java/com/openclaw/manager/openclawserversmanager/audit/dto/AuditLogResponse.java`
  - `src/main/java/com/openclaw/manager/openclawserversmanager/audit/dto/AuditLogFilter.java`
- **Type:** dto
- **Description:** `AuditLogResponse` — all audit log fields. `AuditLogFilter` — optional filter params (userId, action, entityType, entityId, from, to).
- **Dependencies:** AuditAction enum
- **Date:** 2026-03-11

### AuditLogSpecification
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/audit/specification/AuditLogSpecification.java`
- **Type:** specification
- **Description:** Builds dynamic `Specification<AuditLog>` from `AuditLogFilter`. Null filters ignored.
- **Dependencies:** AuditLogFilter DTO, AuditLog entity
- **Date:** 2026-03-11

### AuditService
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/audit/service/AuditService.java`
- **Type:** service
- **Description:** Two `log()` overloads (with/without details) — NEVER throws, wraps in try/catch. Auto-captures IP via AuditContext. `getLogs()` uses Specification-based filtering. `getLogsForEntity()` for entity-specific history.
- **Dependencies:** AuditLogRepository, AuditContext, AuditLogSpecification
- **Date:** 2026-03-11

### AuditController
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/audit/controller/AuditController.java`
- **Type:** controller
- **Description:** GET `/api/v1/audit/logs` — ADMIN only, accepts all filter params as optional `@RequestParam`, paginated. Swagger tagged "audit".
- **Dependencies:** AuditService. SecurityConfig updated: `/api/v1/audit/**` requires ADMIN role.
- **Date:** 2026-03-11

### Audit Retrofit — AuthService + UserService
- **File(s):**
  - `src/main/java/com/openclaw/manager/openclawserversmanager/auth/service/AuthService.java` (modified)
  - `src/main/java/com/openclaw/manager/openclawserversmanager/users/service/UserService.java` (modified)
- **Type:** service (retrofit)
- **Description:** AuthService logs: USER_LOGIN, USER_LOGIN_FAILED (3 paths), USER_LOGOUT. UserService logs: USER_CREATED, USER_UPDATED, USER_DISABLED, USER_DELETED, USER_PASSWORD_CHANGED. All wrapped in try/catch. UserService extracts current user ID from SecurityContext.
- **Dependencies:** AuditService
- **Date:** 2026-03-11

### Audit Dev Page
- **File(s):** `src/main/resources/static/dev/audit.html` (updated from placeholder)
- **Type:** static resource
- **Description:** Audit log viewer with table, filter controls (action, entity type, user ID, date range), pagination, auto-refresh toggle (10s), color-coded actions.
- **Dependencies:** Audit REST API
- **Date:** 2026-03-11
