# Servers Module

## Purpose

Manages the server inventory — remote infrastructure machines that host OpenClaw agents. Stores connection details, tracks server status, and provides SSH connection testing.

## Package

`com.openclaw.manager.openclawserversmanager.servers`

## Components

### Entity: `Server`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| name | String | UNIQUE, NOT NULL |
| hostname | String | NOT NULL |
| ipAddress | String | NOT NULL |
| sshPort | int | NOT NULL, default 22 |
| sshUsername | String | NOT NULL |
| authType | AuthType (enum) | NOT NULL |
| encryptedCredentialId | UUID | FK → Secret (nullable if key-based with agent) |
| environment | String | e.g., "production", "staging", "development" |
| rootDomain | String | nullable |
| subdomain | String | nullable |
| sslEnabled | boolean | default false |
| status | ServerStatus (enum) | NOT NULL, default UNKNOWN |
| metadata | JSON/Map | nullable, flexible key-value store |
| createdAt | Instant | auto-set |
| updatedAt | Instant | auto-set on update |

### Enums

**`AuthType`**: `PASSWORD`, `PRIVATE_KEY`

**`ServerStatus`**: `ONLINE`, `OFFLINE`, `UNKNOWN`, `ERROR`

### DTOs

**`CreateServerRequest`**
- `name` — `@NotBlank @Size(max = 100)`
- `hostname` — `@NotBlank @Pattern(regexp = valid hostname pattern)`
- `ipAddress` — `@NotBlank @Pattern(regexp = valid IPv4/IPv6 pattern)`
- `sshPort` — `@Min(1) @Max(65535)`, default 22
- `sshUsername` — `@NotBlank`
- `authType` — `@NotNull`
- `credentialId` — UUID (references a secret)
- `environment` — `@NotBlank`
- `rootDomain` — optional
- `subdomain` — optional

**`UpdateServerRequest`**
- All fields optional (partial update via PATCH)

**`ServerResponse`**
- All entity fields **except** `encryptedCredentialId` — return `credentialId` only, never the actual secret

**`TestConnectionResponse`**
- `success` — boolean
- `message` — connection result description
- `latencyMs` — connection latency

### Service: `ServerService`

- `createServer(CreateServerRequest)` — validates, saves, returns response
- `getAllServers(Pageable)` — paginated list
- `getServerById(UUID)` — single server
- `updateServer(UUID, UpdateServerRequest)` — partial update
- `deleteServer(UUID)` — removes server
- `testConnection(UUID)` — delegates to SSH module to test connectivity

### Repository: `ServerRepository`

- `findByName(String)` → `Optional<Server>`
- `existsByName(String)` → `boolean`
- `findByEnvironment(String)` → `List<Server>`
- `findByStatus(ServerStatus)` → `List<Server>`

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/servers` | Yes | Register new server |
| GET | `/api/v1/servers` | Yes | List all servers (paginated) |
| GET | `/api/v1/servers/{id}` | Yes | Get server details |
| PATCH | `/api/v1/servers/{id}` | Yes | Update server |
| DELETE | `/api/v1/servers/{id}` | Yes (ADMIN) | Remove server |
| POST | `/api/v1/servers/{id}/test-connection` | Yes | Test SSH connection |

## Business Rules

- Server name must be unique
- `credentialId` must reference an existing secret of matching type (PASSWORD → SSH_PASSWORD, PRIVATE_KEY → SSH_PRIVATE_KEY)
- Deleting a server should check for active deployments first (warn or block)
- Test connection uses the SSH module — does not store connection result permanently (updates `status` field)
- Server metadata is a flexible JSON field for tags, labels, or provider-specific info

## Security Considerations

- Never return actual SSH credentials in API responses — only the `credentialId` reference
- Server deletion restricted to ADMIN role
- Log all server creation/deletion events to audit

## Dependencies

- **secrets** — to resolve `credentialId` to actual SSH credentials for connections
- **ssh** — for test connection functionality
- **audit** — to log server CRUD operations
