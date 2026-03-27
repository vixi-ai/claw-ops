# Task 8: Server Registry Module

**Status:** DONE
**Module(s):** servers
**Priority:** HIGH
**Created:** 2026-03-11
**Completed:** 2026-03-11

## Description
Implement the server registry — CRUD for remote infrastructure machines that host OpenClaw agents. Each server stores connection details (hostname, IP, SSH port, username), references an encrypted credential from the secrets module via `credentialId`, and tracks status. The `testConnection` endpoint is a stub for now (returns a placeholder) — actual SSH connectivity comes in the SSH module task.

## Acceptance Criteria

### Enums
- [ ] `AuthType` enum: `PASSWORD`, `PRIVATE_KEY`
- [ ] `ServerStatus` enum: `ONLINE`, `OFFLINE`, `UNKNOWN`, `ERROR`

### Entity & Migration
- [ ] `Server` JPA entity — UUID PK, name (unique), hostname, ipAddress, sshPort (default 22), sshUsername, authType (enum), credentialId (FK→Secret, nullable), environment, rootDomain (nullable), subdomain (nullable), sslEnabled (default false), status (default UNKNOWN), metadata (JSON text), createdAt, updatedAt
- [ ] Flyway migration `V6__create_servers_table.sql` with indexes on name, environment, status
- [ ] `credentialId` is a FK to `secrets(id)` with `ON DELETE SET NULL` — if the secret is deleted, the server keeps its record but loses the credential reference

### Repository
- [ ] `ServerRepository extends JpaRepository<Server, UUID>`
- [ ] `findByName(String)` → `Optional<Server>`
- [ ] `existsByName(String)` → `boolean`
- [ ] `findByEnvironment(String, Pageable)` → `Page<Server>`
- [ ] `findByStatus(ServerStatus)` → `List<Server>`
- [ ] `existsByCredentialId(UUID)` → `boolean` (to check if a secret is referenced before deletion)

### DTOs
- [ ] `CreateServerRequest` — name (`@NotBlank @Size(max=100)`), hostname (`@NotBlank`), ipAddress (`@NotBlank`), sshPort (`@Min(1) @Max(65535)`, default 22), sshUsername (`@NotBlank`), authType (`@NotNull`), credentialId (UUID, optional), environment (`@NotBlank`), rootDomain (optional), subdomain (optional)
- [ ] `UpdateServerRequest` — all fields optional (partial update)
- [ ] `ServerResponse` — all entity fields; `credentialId` exposed as UUID reference only (never the actual secret)
- [ ] `TestConnectionResponse` — success (boolean), message (String), latencyMs (Long nullable)

### Mapper
- [ ] `ServerMapper` — static methods: `toResponse(Server)`, `toEntity(CreateServerRequest)`

### Service
- [ ] `ServerService` with constructor injection of `ServerRepository`, `SecretService`, `AuditService`
- [ ] `createServer(CreateServerRequest, UUID currentUserId)` — validate unique name, validate credentialId references an existing secret of matching type (PASSWORD→SSH_PASSWORD, PRIVATE_KEY→SSH_PRIVATE_KEY), save, audit `SERVER_CREATED`
- [ ] `getAllServers(Pageable)` → `Page<ServerResponse>`
- [ ] `getServerById(UUID)` → `ServerResponse`
- [ ] `updateServer(UUID, UpdateServerRequest, UUID currentUserId)` — partial update, re-validate credentialId if changed, audit `SERVER_UPDATED`
- [ ] `deleteServer(UUID, UUID currentUserId)` — remove server, audit `SERVER_DELETED`
- [ ] `testConnection(UUID)` → `TestConnectionResponse` — **stub for now**: returns `{ success: false, message: "SSH module not implemented yet" }`. Will be wired to SSH module later.
- [ ] All write methods annotated with `@Transactional`
- [ ] Duplicate name check on create/update (throw `DuplicateResourceException`)
- [ ] Credential type validation: if `authType=PASSWORD`, credentialId must reference a secret with `type=SSH_PASSWORD`; if `authType=PRIVATE_KEY`, must reference `SSH_PRIVATE_KEY`
- [ ] Audit calls wrapped in try/catch

### Controller
- [ ] `ServerController` at `/api/v1/servers`
- [ ] `POST /` — create server (authenticated)
- [ ] `GET /` — list all servers, paginated (authenticated)
- [ ] `GET /{id}` — get server details (authenticated)
- [ ] `PATCH /{id}` — update server (authenticated)
- [ ] `DELETE /{id}` — delete server (ADMIN only)
- [ ] `POST /{id}/test-connection` — test SSH connection (authenticated)
- [ ] All request bodies validated with `@Valid`
- [ ] Swagger `@Tag(name = "Servers")` and `@Operation` annotations

### Security Config Update
- [ ] SecurityConfig: `DELETE /api/v1/servers/**` requires ADMIN role, other methods require authentication

### Metadata Field
- [ ] `metadata` stored as TEXT column containing JSON (not PostgreSQL `jsonb` — keeps it simple and portable)
- [ ] Mapped as `String` in entity, serialized/deserialized as `Map<String, Object>` in DTOs using Jackson
- [ ] Optional field — can be null

### Dev Admin Page
- [ ] Update `/dev/servers.html` from placeholder to functional page:
  - Table showing servers (name, hostname, IP, port, environment, status, authType)
  - Create server form (all fields, credential dropdown populated from `/api/v1/secrets`)
  - Edit server form
  - Delete button with confirmation (ADMIN only)
  - Test connection button per server (shows result)
  - Status badge colors (ONLINE=green, OFFLINE=red, UNKNOWN=gray, ERROR=orange)
  - Pagination controls

## Implementation Notes

### Migration SQL
```sql
-- V6__create_servers_table.sql
CREATE TABLE servers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    hostname VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    ssh_port INTEGER NOT NULL DEFAULT 22,
    ssh_username VARCHAR(100) NOT NULL,
    auth_type VARCHAR(20) NOT NULL,
    credential_id UUID REFERENCES secrets(id) ON DELETE SET NULL,
    environment VARCHAR(50) NOT NULL,
    root_domain VARCHAR(255),
    subdomain VARCHAR(255),
    ssl_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_servers_name ON servers(name);
CREATE INDEX idx_servers_environment ON servers(environment);
CREATE INDEX idx_servers_status ON servers(status);
CREATE INDEX idx_servers_credential_id ON servers(credential_id);
```

### Credential validation pattern
```java
// In ServerService.createServer():
if (request.credentialId() != null) {
    SecretResponse secret = secretService.getSecretById(request.credentialId());
    validateCredentialType(request.authType(), secret.type());
}

private void validateCredentialType(AuthType authType, SecretType secretType) {
    boolean valid = switch (authType) {
        case PASSWORD -> secretType == SecretType.SSH_PASSWORD;
        case PRIVATE_KEY -> secretType == SecretType.SSH_PRIVATE_KEY;
    };
    if (!valid) {
        throw new IllegalArgumentException(
            "Auth type %s requires a secret of type %s, but got %s".formatted(
                authType,
                authType == AuthType.PASSWORD ? "SSH_PASSWORD" : "SSH_PRIVATE_KEY",
                secretType));
    }
}
```

### SecretService integration note
The `ServerService` needs a method on `SecretService` to check if a secret exists and get its type without decrypting. `getSecretById(UUID)` already returns `SecretResponse` which includes `type` — use that.

Also add `existsByCredentialId` check: before deleting a secret in `SecretService`, optionally warn/block if servers reference it. This can be a future enhancement — for now, `ON DELETE SET NULL` handles it at the DB level.

### Recommended implementation order
1. `AuthType` enum
2. `ServerStatus` enum
3. `Server` entity + `V6` migration
4. `ServerRepository`
5. `CreateServerRequest`, `UpdateServerRequest`, `ServerResponse`, `TestConnectionResponse` DTOs
6. `ServerMapper`
7. `ServerService` (with credential validation)
8. `ServerController` + SecurityConfig update
9. Update `servers.html` dev page
10. Update architecture log

### Package structure
```
com.openclaw.manager.openclawserversmanager/
└── servers/
    ├── controller/ServerController.java
    ├── dto/CreateServerRequest.java
    ├── dto/UpdateServerRequest.java
    ├── dto/ServerResponse.java
    ├── dto/TestConnectionResponse.java
    ├── entity/Server.java
    ├── entity/AuthType.java
    ├── entity/ServerStatus.java
    ├── mapper/ServerMapper.java
    ├── repository/ServerRepository.java
    └── service/ServerService.java
```

## Files Modified
- `src/main/java/.../servers/entity/AuthType.java` (NEW)
- `src/main/java/.../servers/entity/ServerStatus.java` (NEW)
- `src/main/java/.../servers/entity/Server.java` (NEW)
- `src/main/resources/db/migration/V6__create_servers_table.sql` (NEW)
- `src/main/java/.../servers/repository/ServerRepository.java` (NEW)
- `src/main/java/.../servers/dto/CreateServerRequest.java` (NEW)
- `src/main/java/.../servers/dto/UpdateServerRequest.java` (NEW)
- `src/main/java/.../servers/dto/ServerResponse.java` (NEW)
- `src/main/java/.../servers/dto/TestConnectionResponse.java` (NEW)
- `src/main/java/.../servers/mapper/ServerMapper.java` (NEW)
- `src/main/java/.../servers/service/ServerService.java` (NEW)
- `src/main/java/.../servers/controller/ServerController.java` (NEW)
- `src/main/java/.../auth/config/SecurityConfig.java` (MODIFIED — added DELETE servers ADMIN rule)
- `src/main/resources/static/dev/servers.html` (REWRITTEN — full CRUD page)
- `src/main/resources/static/dev/index.html` (MODIFIED — Servers badge changed to Active)
