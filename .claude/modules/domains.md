# Domains Module

## Purpose

Provider-agnostic DNS domain management. Manages provider accounts, DNS zones, hostname assignments (auto-generated or custom), and DNS record lifecycle via pluggable provider adapters. Supports multi-provider, multi-zone configurations with desired-state tracking and operational event logging.

## Package

`com.openclaw.manager.openclawserversmanager.domains`

## Core Concepts

### ProviderAccount
Credentials and settings for one DNS provider account (e.g. a Cloudflare API token). References an encrypted `DNS_TOKEN` secret. Tracks health status.

### ManagedZone
A domain/zone (e.g. `example.com`) attached to one provider account. Must be activated via preflight checks before use. Caches the provider-side zone ID.

### DomainAssignment
A hostname record created for a server or custom resource. Tracks lifecycle from PROVISIONING → DNS_CREATED → VERIFIED → RELEASED. Stores desired-state hash for future reconciliation.

### DomainEvent
Granular operational log with provider correlation IDs. Separate from audit log — captures DNS-level operational details (record created, deleted, verified, zone preflight results).

## Entities

### `ProviderAccount`
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| providerType | DnsProviderType | NOT NULL |
| displayName | String | NOT NULL, UNIQUE |
| enabled | boolean | default true |
| credentialId | UUID | FK → secrets, NOT NULL |
| providerSettings | String (JSON) | nullable |
| healthStatus | HealthStatus | NOT NULL, default UNKNOWN |
| createdAt/updatedAt | Instant | auto-set |

### `ManagedZone`
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| zoneName | String | NOT NULL |
| providerAccountId | UUID | FK → provider_accounts, NOT NULL |
| active | boolean | default false |
| defaultTtl | int | default 300 |
| providerZoneId | String | nullable (cached after activation) |
| environmentTag | String | nullable |
| metadata | String (JSON) | nullable |
| createdAt/updatedAt | Instant | auto-set |

### `DomainAssignment`
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| zoneId | UUID | FK → managed_zones, NOT NULL |
| hostname | String | NOT NULL, partial unique (where status != RELEASED) |
| recordType | DnsRecordType | NOT NULL, default A |
| targetValue | String | NOT NULL |
| assignmentType | AssignmentType | NOT NULL |
| resourceId | UUID | nullable |
| status | AssignmentStatus | NOT NULL, default REQUESTED |
| providerRecordId | String | nullable |
| desiredStateHash | String | nullable |
| createdAt/updatedAt | Instant | auto-set |

### `DomainEvent`
| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| assignmentId | UUID | FK → domain_assignments, nullable |
| zoneId | UUID | FK → managed_zones, nullable |
| action | DomainEventAction | NOT NULL |
| outcome | DomainEventOutcome | NOT NULL |
| providerCorrelationId | String | nullable |
| details | String | nullable |
| createdAt | Instant | auto-set (append-only) |

## Enums

- **`DnsProviderType`**: CLOUDFLARE, NAMECHEAP, GODADDY
- **`AssignmentType`**: SERVER, AGENT, CUSTOM
- **`AssignmentStatus`**: REQUESTED, PROVISIONING, DNS_CREATED, VERIFIED, ACTIVE, FAILED, RELEASING, RELEASED
- **`DnsRecordType`**: A, AAAA, CNAME, TXT, MX, NS
- **`DomainEventAction`**: CREDENTIALS_VALIDATED, ZONE_VERIFIED, RECORD_CREATED, RECORD_UPDATED, RECORD_DELETED, RECORD_VERIFIED, ZONE_PREFLIGHT_PASSED, ZONE_PREFLIGHT_FAILED
- **`DomainEventOutcome`**: SUCCESS, FAILURE, PARTIAL
- **`HealthStatus`**: UNKNOWN, HEALTHY, DEGRADED, UNREACHABLE

## DTOs

All Java records with Jakarta Validation.

- `CreateProviderAccountRequest` — providerType(@NotNull), displayName(@NotBlank), credentialId(@NotNull), providerSettings(Map)
- `UpdateProviderAccountRequest` — displayName, enabled, credentialId, providerSettings (all optional)
- `ProviderAccountResponse` — all fields
- `CreateManagedZoneRequest` — zoneName(@NotBlank @Pattern), providerAccountId(@NotNull), defaultTtl, environmentTag, metadata
- `UpdateManagedZoneRequest` — active, defaultTtl, environmentTag, metadata (all optional)
- `ManagedZoneResponse` — all fields
- `AssignServerDomainRequest` — serverId(@NotNull), zoneId(@NotNull), hostnameOverride (optional)
- `AssignCustomDomainRequest` — zoneId(@NotNull), hostname(@NotBlank), recordType(@NotNull), targetValue(@NotBlank), resourceId
- `DomainAssignmentResponse` — all fields + zoneName (denormalized)
- `DomainEventResponse` — all fields
- `ValidateCredentialsResponse` — valid(boolean), message
- `VerifyZoneResponse` — manageable(boolean), message, warnings(List)

## Provider Adapter Pattern

### Interface: `DnsProviderAdapter`
- `getProviderType()`, `getCapabilities()`
- `validateCredentials(token, settings)` → ValidateCredentialsResponse
- `verifyZoneManageable(zoneName, token, settings)` → VerifyZoneResponse
- `resolveZoneId(zoneName, token, settings)` → String
- `createOrUpsertRecord(zoneName, providerZoneId, record, token, settings)` → DnsRecord
- `deleteRecord(providerZoneId, recordId, token, settings)`
- `listRecords(providerZoneId, token, settings)` → List<DnsRecord>

### `ProviderAdapterFactory`
Spring component that collects all `DnsProviderAdapter` beans and provides lookup by `DnsProviderType`.

### `HostnameStrategy`
Pluggable hostname generation. Default `SlugBasedHostnameStrategy`: lowercases, replaces spaces/underscores with hyphens, strips non-alphanumeric.

### Cloudflare Adapter
First provider implementation. Uses `RestClient` against `https://api.cloudflare.com/client/v4`. Supports proxied records, zone verification, credential validation.

## API Endpoints

### Provider Accounts (`/api/v1/provider-accounts`)
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/` | Yes | Create provider account |
| GET | `/` | Yes | List accounts (paginated) |
| GET | `/{id}` | Yes | Get account by ID |
| PATCH | `/{id}` | Yes | Update account |
| DELETE | `/{id}` | ADMIN | Delete account |
| POST | `/{id}/validate` | Yes | Validate credentials |
| GET | `/{id}/capabilities` | Yes | Get provider capabilities |

### Managed Zones (`/api/v1/zones`)
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/` | Yes | Create zone |
| GET | `/` | Yes | List zones (paginated) |
| GET | `/{id}` | Yes | Get zone by ID |
| PATCH | `/{id}` | Yes | Update zone |
| DELETE | `/{id}` | ADMIN | Delete zone |
| POST | `/{id}/activate` | Yes | Run preflight + activate |
| GET | `/{id}/events` | Yes | Get zone events |

### Domain Assignments (`/api/v1/domain-assignments`)
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/server` | Yes | Assign server hostname |
| POST | `/custom` | Yes | Assign custom DNS record |
| GET | `/` | Yes | List assignments (filterable) |
| GET | `/{id}` | Yes | Get assignment by ID |
| POST | `/{id}/verify` | Yes | Verify DNS propagation |
| DELETE | `/{id}` | ADMIN | Release assignment |
| DELETE | `/resource/{resourceId}` | ADMIN | Release all for resource |
| GET | `/{id}/events` | Yes | Get assignment events |

## Business Rules

- Provider account credential must be a secret of type `DNS_TOKEN`
- Zone must be activated (preflight passed) before assignments can be created
- Hostname must be unique among non-released assignments (partial unique constraint)
- Deleting a zone requires all assignments to be released first
- Deleting a provider account requires no zones referencing it
- Assignment release attempts DNS record deletion but marks as RELEASED regardless (best-effort)

## Exceptions

- **`DomainException`** → 422 Unprocessable Entity (business rule violations)
- **`DnsProviderException`** extends DomainException → 502 Bad Gateway (provider API failures, includes providerCorrelationId)

## Dependencies

- **secrets** — decrypt DNS provider tokens via `SecretService`
- **servers** — resolve server IP/hostname for server domain assignments via `ServerService`
- **audit** — log domain operations via `AuditService`

## Future Work

- Namecheap and GoDaddy adapters
- SSL automation (certbot via SSH, reverse proxy config)
- Reconciliation scheduler (drift detection using desiredStateHash)
- Auto-assign domain on server creation hook
