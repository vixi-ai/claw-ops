# Servers — Architecture Log

Living documentation of implemented code. Agents MUST append here after writing code for this module.

## Implemented Components

### AuthType + ServerStatus Enums
- **File(s):** `src/main/java/.../servers/entity/AuthType.java`, `ServerStatus.java`
- **Type:** enum
- **Description:** AuthType: PASSWORD, PRIVATE_KEY. ServerStatus: ONLINE, OFFLINE, UNKNOWN, ERROR.
- **Date:** 2026-03-11

### Server Entity + Migration
- **File(s):** `src/main/java/.../servers/entity/Server.java`, `db/migration/V6__create_servers_table.sql`
- **Type:** entity + migration
- **Description:** UUID PK, name (unique), hostname, ipAddress, sshPort (default 22), sshUsername, authType (enum), credentialId (FK→secrets ON DELETE SET NULL), environment, rootDomain, subdomain, sslEnabled, status (default UNKNOWN), metadata (TEXT/JSON), createdAt, updatedAt. Indexes on name, environment, status, credential_id.
- **Date:** 2026-03-11

### ServerRepository
- **File(s):** `src/main/java/.../servers/repository/ServerRepository.java`
- **Type:** repository
- **Description:** JpaRepository with findByName, existsByName, findByEnvironment(Pageable), findByStatus, existsByCredentialId.
- **Date:** 2026-03-11

### DTOs
- **File(s):** `CreateServerRequest.java`, `UpdateServerRequest.java`, `ServerResponse.java`, `TestConnectionResponse.java`
- **Type:** dto
- **Description:** CreateServerRequest: validated with @NotBlank/@NotNull/@Min/@Max, sshPort defaults to 22. UpdateServerRequest: all fields optional for partial update. ServerResponse: all entity fields, metadata as Map. TestConnectionResponse: success/message/latencyMs.
- **Date:** 2026-03-11

### ServerMapper
- **File(s):** `src/main/java/.../servers/mapper/ServerMapper.java`
- **Type:** mapper
- **Description:** Static toResponse/toEntity. Handles JSON serialization/deserialization of metadata field using Jackson ObjectMapper.
- **Date:** 2026-03-11

### ServerService
- **File(s):** `src/main/java/.../servers/service/ServerService.java`
- **Type:** service
- **Description:** Full CRUD + testConnection stub. Validates unique name, validates credentialId type matches authType (PASSWORD→SSH_PASSWORD, PRIVATE_KEY→SSH_PRIVATE_KEY). Audit logging on create/update/delete. @Transactional on writes.
- **Dependencies:** SecretService, AuditService, ServerRepository
- **Date:** 2026-03-11

### ServerController + Security
- **File(s):** `src/main/java/.../servers/controller/ServerController.java`, `SecurityConfig.java`
- **Type:** controller + config
- **Description:** /api/v1/servers REST endpoints. POST (create), GET (list/detail), PATCH (update), DELETE (ADMIN only), POST /{id}/test-connection. SecurityConfig updated: DELETE /api/v1/servers/** requires ROLE_ADMIN.
- **Date:** 2026-03-11

### Dev Page: servers.html
- **File(s):** `static/dev/servers.html`, `static/dev/index.html`
- **Type:** static resource
- **Description:** Full servers management page with table (name, hostname/IP, port, environment, auth, status), create/edit modal with credential dropdown from secrets API, test connection button, delete with confirmation. Status badge colors (ONLINE=green, OFFLINE=red, UNKNOWN=gray, ERROR=orange). Dashboard shows Servers as Active.
- **Date:** 2026-03-11

### ServerService — Auto-Assign Domain on Create/Delete
- **File(s):** `servers/service/ServerService.java` (modified)
- **Type:** service (modified)
- **Description:** Injected `DomainAssignmentService` with `@Lazy` to break circular dependency (ServerService ↔ DomainAssignmentService). `createServer()` now calls `autoAssignServerDomain()` after save (fire-and-forget, wrapped in try-catch). `deleteServer()` now calls `releaseAllForResource()` before deletion to clean up DNS records. Both operations are best-effort and never fail the primary operation.
- **Dependencies:** DomainAssignmentService (lazy)
- **Date:** 2026-03-12
