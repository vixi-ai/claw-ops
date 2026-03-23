# Domains — Architecture Log

Living documentation of implemented code. Agents MUST append here after writing code for this module.

## Implemented Components

### Flyway Migration V9
- **File(s):** `src/main/resources/db/migration/V9__create_domain_tables.sql`
- **Type:** migration
- **Description:** Creates 4 tables (provider_accounts, managed_zones, domain_assignments, domain_events) with indexes. Uses ON DELETE RESTRICT to prevent cascade deletion of live DNS. Partial unique index on hostname where status != 'RELEASED'. Provider zone ID cached on managed_zones.
- **Dependencies:** secrets table (FK from provider_accounts.credential_id)
- **Date:** 2026-03-12

### Enums (7 files)
- **File(s):** `domains/entity/DnsProviderType.java`, `AssignmentType.java`, `AssignmentStatus.java`, `DnsRecordType.java`, `DomainEventAction.java`, `DomainEventOutcome.java`, `HealthStatus.java`
- **Type:** entity (enums)
- **Description:** All domain lifecycle enums. Used with @Enumerated(EnumType.STRING) in entities.
- **Dependencies:** None
- **Date:** 2026-03-12

### ProviderAccount Entity
- **File(s):** `domains/entity/ProviderAccount.java`
- **Type:** entity
- **Description:** JPA entity for DNS provider accounts. UUID PK, unique displayName, references credential in secrets table. Stores providerSettings as JSON TEXT. Tracks healthStatus. @PreUpdate for updatedAt.
- **Dependencies:** secrets module (credentialId FK)
- **Date:** 2026-03-12

### ManagedZone Entity
- **File(s):** `domains/entity/ManagedZone.java`
- **Type:** entity
- **Description:** JPA entity for DNS zones. References providerAccountId. Caches providerZoneId after activation. Stores metadata as JSON TEXT. Default TTL 300.
- **Dependencies:** ProviderAccount (providerAccountId)
- **Date:** 2026-03-12

### DomainAssignment Entity
- **File(s):** `domains/entity/DomainAssignment.java`
- **Type:** entity
- **Description:** JPA entity for hostname assignments. References zoneId. Tracks status lifecycle (PROVISIONING -> DNS_CREATED -> VERIFIED -> RELEASED). Stores providerRecordId and desiredStateHash for reconciliation.
- **Dependencies:** ManagedZone (zoneId)
- **Date:** 2026-03-12

### DomainEvent Entity
- **File(s):** `domains/entity/DomainEvent.java`
- **Type:** entity
- **Description:** Append-only operational event log. References assignment and/or zone. Stores provider correlation IDs for debugging. No updatedAt (immutable).
- **Dependencies:** DomainAssignment, ManagedZone
- **Date:** 2026-03-12

### DTOs (12 records)
- **File(s):** `domains/dto/Create*.java`, `Update*.java`, `*Response.java`, `Assign*.java`, `Validate*.java`, `Verify*.java`
- **Type:** dto
- **Description:** Java records with Jakarta Validation annotations. CreateManagedZoneRequest has compact constructor defaulting TTL to 300. DomainAssignmentResponse includes denormalized zoneName.
- **Dependencies:** Enums
- **Date:** 2026-03-12

### Mappers (4 classes)
- **File(s):** `domains/mapper/ProviderAccountMapper.java`, `ManagedZoneMapper.java`, `DomainAssignmentMapper.java`, `DomainEventMapper.java`
- **Type:** mapper
- **Description:** Final classes with static methods. ProviderAccountMapper and ManagedZoneMapper handle JSON serialization/deserialization for settings/metadata using Jackson ObjectMapper.
- **Dependencies:** Entities, DTOs
- **Date:** 2026-03-12

### Repositories (4 interfaces)
- **File(s):** `domains/repository/ProviderAccountRepository.java`, `ManagedZoneRepository.java`, `DomainAssignmentRepository.java`, `DomainEventRepository.java`
- **Type:** repository
- **Description:** Spring Data JPA repositories with derived query methods. DomainAssignmentRepository has findByHostnameAndStatusNot for hostname uniqueness checks, existsByZoneIdAndStatusNot for zone deletion safety.
- **Dependencies:** Entities
- **Date:** 2026-03-12

### Exceptions
- **File(s):** `domains/exception/DomainException.java`, `DnsProviderException.java`
- **Type:** exception
- **Description:** DomainException (422) for business rule violations. DnsProviderException extends it (502) with providerCorrelationId for provider API failures. Both handled in GlobalExceptionHandler (DnsProviderException handler placed before DomainException since it's a subclass).
- **Dependencies:** GlobalExceptionHandler (modified)
- **Date:** 2026-03-12

### Provider Adapter Interface + Factory
- **File(s):** `domains/provider/DnsProviderAdapter.java`, `ProviderCapabilities.java`, `DnsRecord.java`, `ProviderAdapterFactory.java`
- **Type:** provider abstraction
- **Description:** DnsProviderAdapter interface defines contract for all DNS providers. ProviderCapabilities record reports provider features. DnsRecord is the provider-neutral record representation. ProviderAdapterFactory collects all adapter beans via Spring's List injection and provides lookup by DnsProviderType.
- **Dependencies:** Enums, DTOs (ValidateCredentialsResponse, VerifyZoneResponse)
- **Date:** 2026-03-12

### Cloudflare Adapter
- **File(s):** `domains/provider/cloudflare/CloudflareAdapter.java`, `CloudflareModels.java`
- **Type:** provider implementation
- **Description:** First DNS provider adapter. Uses RestClient against Cloudflare API v4. Supports credential validation, zone preflight, record CRUD (create-or-upsert pattern), zone ID resolution. CloudflareModels contains inner records for API response parsing.
- **Dependencies:** DnsProviderAdapter, RestClient
- **Date:** 2026-03-12

### HostnameStrategy
- **File(s):** `domains/naming/HostnameStrategy.java`, `SlugBasedHostnameStrategy.java`, `HostnameStrategyConfig.java`
- **Type:** naming strategy
- **Description:** Pluggable hostname generation. SlugBasedHostnameStrategy: lowercases, replaces spaces/underscores with hyphens, strips non-alphanumeric, collapses hyphens. HostnameStrategyConfig registers default bean.
- **Dependencies:** None
- **Date:** 2026-03-12

### DomainEventService
- **File(s):** `domains/service/DomainEventService.java`
- **Type:** service
- **Description:** Records operational events with try-catch (never throws). Read methods for assignment and zone event history.
- **Dependencies:** DomainEventRepository
- **Date:** 2026-03-12

### ProviderAccountService
- **File(s):** `domains/service/ProviderAccountService.java`
- **Type:** service
- **Description:** CRUD with audit logging. validateCredentials decrypts token via SecretService, calls adapter, updates healthStatus. deleteAccount checks no zones reference it. Validates secret type is DNS_TOKEN.
- **Dependencies:** ProviderAccountRepository, SecretService, ProviderAdapterFactory, AuditService
- **Date:** 2026-03-12

### ManagedZoneService
- **File(s):** `domains/service/ManagedZoneService.java`
- **Type:** service
- **Description:** CRUD with audit logging. activateZone runs preflight via adapter, caches providerZoneId, sets active=true. deleteZone checks no active assignments. Duplicate zone check per provider account.
- **Dependencies:** ManagedZoneRepository, DomainAssignmentRepository, ProviderAccountService, ProviderAdapterFactory, SecretService, DomainEventService, AuditService
- **Date:** 2026-03-12

### DomainAssignmentService
- **File(s):** `domains/service/DomainAssignmentService.java`
- **Type:** service
- **Description:** Core orchestration. assignServerDomain resolves server IP via ServerService, generates hostname via HostnameStrategy, creates assignment, calls adapter. assignCustomDomain for explicit records. releaseAssignment does best-effort DNS deletion then marks RELEASED. verifyAssignment lists records and checks match. computeStateHash uses SHA-256 for reconciliation.
- **Dependencies:** DomainAssignmentRepository, ManagedZoneService, ProviderAccountService, ProviderAdapterFactory, SecretService, ServerService, HostnameStrategy, DomainEventService, AuditService
- **Date:** 2026-03-12

### ProviderAccountController
- **File(s):** `domains/controller/ProviderAccountController.java`
- **Type:** controller
- **Description:** REST controller at /api/v1/provider-accounts. CRUD + validate credentials + get capabilities. DELETE is ADMIN-only via SecurityConfig.
- **Dependencies:** ProviderAccountService
- **Date:** 2026-03-12

### ManagedZoneController
- **File(s):** `domains/controller/ManagedZoneController.java`
- **Type:** controller
- **Description:** REST controller at /api/v1/zones. CRUD + activate zone + get zone events. DELETE is ADMIN-only.
- **Dependencies:** ManagedZoneService, DomainEventService
- **Date:** 2026-03-12

### DomainAssignmentController
- **File(s):** `domains/controller/DomainAssignmentController.java`
- **Type:** controller
- **Description:** REST controller at /api/v1/domain-assignments. Assign server domain, assign custom, list (filterable by zoneId/resourceId), verify, release, release all for resource, get events. DELETE is ADMIN-only.
- **Dependencies:** DomainAssignmentService, DomainEventService
- **Date:** 2026-03-12

### AuditAction Updates
- **File(s):** `audit/entity/AuditAction.java` (modified)
- **Type:** enum (modified)
- **Description:** Added 9 new values: PROVIDER_ACCOUNT_CREATED, PROVIDER_ACCOUNT_UPDATED, PROVIDER_ACCOUNT_DELETED, ZONE_CREATED, ZONE_ACTIVATED, ZONE_DELETED, DOMAIN_ASSIGNED, DOMAIN_RELEASED, DOMAIN_VERIFIED.
- **Dependencies:** None
- **Date:** 2026-03-12

### SecurityConfig Updates
- **File(s):** `auth/config/SecurityConfig.java` (modified)
- **Type:** config (modified)
- **Description:** Added ADMIN-only DELETE rules for /api/v1/provider-accounts/**, /api/v1/zones/**, /api/v1/domain-assignments/**.
- **Dependencies:** None
- **Date:** 2026-03-12

### GlobalExceptionHandler Updates
- **File(s):** `common/exception/GlobalExceptionHandler.java` (modified)
- **Type:** exception handler (modified)
- **Description:** Added DnsProviderException handler (502 Bad Gateway) and DomainException handler (422 Unprocessable Entity). DnsProviderException handler placed before DomainException since it's a subclass.
- **Dependencies:** DomainException, DnsProviderException
- **Date:** 2026-03-12

### Dev Admin Page
- **File(s):** `src/main/resources/static/dev/domains.html`, `static/dev/index.html` (modified)
- **Type:** frontend (dev page)
- **Description:** Three-tab page for Provider Accounts, Managed Zones, and Domain Assignments. Supports create, validate, activate, verify, release operations via modals. Index.html updated with Active badge.
- **Dependencies:** All domain controllers
- **Date:** 2026-03-12

### Flyway Migration V10 — Auto-Assign Default Zone
- **File(s):** `src/main/resources/db/migration/V10__add_auto_assign_to_zones.sql`
- **Type:** migration
- **Description:** Adds `default_for_auto_assign` BOOLEAN column (default FALSE) to managed_zones. Partial unique index ensures only one zone can be the default at a time.
- **Dependencies:** V9 migration (managed_zones table)
- **Date:** 2026-03-12

### ManagedZone Entity — defaultForAutoAssign Field
- **File(s):** `domains/entity/ManagedZone.java` (modified)
- **Type:** entity (modified)
- **Description:** Added `defaultForAutoAssign` boolean field with getter/setter. Mapped to `default_for_auto_assign` column.
- **Dependencies:** V10 migration
- **Date:** 2026-03-12

### ManagedZone DTO/Mapper/Repository Updates for Auto-Assign
- **File(s):** `domains/dto/ManagedZoneResponse.java`, `domains/mapper/ManagedZoneMapper.java`, `domains/repository/ManagedZoneRepository.java` (all modified)
- **Type:** dto, mapper, repository (modified)
- **Description:** ManagedZoneResponse includes `defaultForAutoAssign` field. Mapper maps it. Repository adds `findByDefaultForAutoAssignTrue()` and `clearDefaultAutoAssign()` (@Modifying JPQL update).
- **Dependencies:** ManagedZone entity
- **Date:** 2026-03-12

### ManagedZoneService — Auto-Assign Default Methods
- **File(s):** `domains/service/ManagedZoneService.java` (modified)
- **Type:** service (modified)
- **Description:** Added `setDefaultForAutoAssign(zoneId, userId)` — clears existing default, sets new one (zone must be active). Added `getDefaultAutoAssignZone()` — returns the default zone or empty. Audit logged.
- **Dependencies:** ManagedZoneRepository
- **Date:** 2026-03-12

### ManagedZoneController — Set Default Endpoint
- **File(s):** `domains/controller/ManagedZoneController.java` (modified)
- **Type:** controller (modified)
- **Description:** Added `POST /api/v1/zones/{id}/set-default` endpoint calling `setDefaultForAutoAssign()`.
- **Dependencies:** ManagedZoneService
- **Date:** 2026-03-12

### DomainAssignmentService — Auto-Assign & Hostname Uniqueness
- **File(s):** `domains/service/DomainAssignmentService.java` (modified)
- **Type:** service (modified)
- **Description:** Added `autoAssignServerDomain(serverId, serverName, serverIp, userId)` — finds default zone, resolves unique hostname, provisions DNS, logs audit with DOMAIN_AUTO_ASSIGNED. Added `resolveUniqueHostname()` — tries base hostname then `-2` to `-99` suffixes. Added `isHostnameAvailable()` and `slugify()` helpers. All failures are caught and logged, never propagated.
- **Dependencies:** ManagedZoneService, HostnameStrategy, DomainAssignmentRepository, ProviderAdapterFactory, AuditService
- **Date:** 2026-03-12

### AuditAction — DOMAIN_AUTO_ASSIGNED
- **File(s):** `audit/entity/AuditAction.java` (modified)
- **Type:** enum (modified)
- **Description:** Added `DOMAIN_AUTO_ASSIGNED` value for auto-provisioned domain audit events.
- **Dependencies:** None
- **Date:** 2026-03-12

### domains.html — Default Zone UI
- **File(s):** `src/main/resources/static/dev/domains.html` (modified)
- **Type:** frontend (modified)
- **Description:** Managed Zones table now shows "Default" column with green badge. Active zones that are not default show a "Set Default" button calling `POST /zones/{id}/set-default`.
- **Dependencies:** ManagedZoneController
- **Date:** 2026-03-12

### Namecheap Adapter
- **File(s):** `domains/provider/namecheap/NamecheapAdapter.java`
- **Type:** provider implementation
- **Description:** Namecheap DNS provider adapter. Uses XML API with API key + username + auto-detected client IP (cached via `https://api.ipify.org`). Supports credential validation, zone verification, record CRUD, domain listing. `listDomains()` fetches all pages of `domains.getList` with XML parsing. PageSize=10 (Namecheap minimum). `resolveClientIp()` auto-detects server public IP.
- **Dependencies:** DnsProviderAdapter, RestClient
- **Date:** 2026-03-12

### DiscoveredDomain Record
- **File(s):** `domains/provider/DiscoveredDomain.java`
- **Type:** record
- **Description:** Represents a domain discovered from a provider account. Fields: `domainName`, `providerZoneId`, `manageable`. Used by `listDomains()` in adapters and `syncDomainsForAccount()` in service.
- **Dependencies:** None
- **Date:** 2026-03-12

### SyncDomainsResponse DTO
- **File(s):** `domains/dto/SyncDomainsResponse.java`
- **Type:** dto
- **Description:** Response for domain sync operations. Fields: `total` (discovered), `imported` (new), `skipped` (already existed).
- **Dependencies:** None
- **Date:** 2026-03-12

### DnsProviderAdapter — listDomains() Method
- **File(s):** `domains/provider/DnsProviderAdapter.java` (modified)
- **Type:** interface (modified)
- **Description:** Added `listDomains(String decryptedToken, Map<String, Object> settings)` returning `List<DiscoveredDomain>`. Implemented by CloudflareAdapter (paginated `GET /zones`) and NamecheapAdapter (paginated `domains.getList`).
- **Dependencies:** DiscoveredDomain
- **Date:** 2026-03-12

### CloudflareAdapter — listDomains() Implementation
- **File(s):** `domains/provider/cloudflare/CloudflareAdapter.java` (modified)
- **Type:** provider implementation (modified)
- **Description:** Implements `listDomains()` via paginated `GET /zones` (50 per page). Maps zone name → domainName, zone id → providerZoneId, status=="active" → manageable.
- **Dependencies:** CloudflareModels
- **Date:** 2026-03-12

### ProviderAccountService — syncDomainsForAccount()
- **File(s):** `domains/service/ProviderAccountService.java` (modified)
- **Type:** service (modified)
- **Description:** Added `syncDomainsForAccount(accountId, userId)` — calls adapter.listDomains(), creates ManagedZone for each new domain (matched by zoneName + providerAccountId), sets active if manageable, caches providerZoneId. Auto-triggered after successful credential validation. Returns SyncDomainsResponse.
- **Dependencies:** ManagedZoneRepository, ProviderAdapterFactory, SecretService
- **Date:** 2026-03-12

### ProviderAccountController — Sync Domains Endpoint
- **File(s):** `domains/controller/ProviderAccountController.java` (modified)
- **Type:** controller (modified)
- **Description:** Added `POST /api/v1/provider-accounts/{id}/sync-domains` endpoint for manual domain re-sync.
- **Dependencies:** ProviderAccountService
- **Date:** 2026-03-12

### domains.html — Redesign (Task 14)
- **File(s):** `src/main/resources/static/dev/domains.html` (rewritten)
- **Type:** frontend (rewritten)
- **Description:** Redesigned from 3 tabs to 2 sections: (1) Provider Accounts & Domains — accounts table with expandable rows showing auto-imported domains, Sync/Validate/Delete buttons per account, Set Default/Delete per domain; (2) Subdomains — flat list of domain assignments with zone filter, Verify/Release buttons, Custom Record modal. Removed separate "Create Zone" modal and "Activate Zone" button — zones come from provider sync and auto-activate if manageable.
- **Dependencies:** All domain controllers
- **Date:** 2026-03-12

### SSL Automation (Task 15)

#### V11 Migration + SslStatus + SslCertificate + Repository + DTOs + Mapper + SslConfig
- **File(s):** `V11__create_ssl_certificates_table.sql`, `domains/entity/SslStatus.java`, `domains/entity/SslCertificate.java`, `domains/repository/SslCertificateRepository.java`, `domains/dto/SslCertificateResponse.java`, `domains/dto/ProvisionSslRequest.java`, `domains/mapper/SslCertificateMapper.java`, `domains/config/SslConfig.java`
- **Type:** migration, entity, enum, repository, dto, mapper, config
- **Description:** SSL certificate tracking infrastructure. SslStatus: PENDING/PROVISIONING/ACTIVE/FAILED/EXPIRED/REMOVING. Entity tracks per-server SSL lifecycle with hostname, admin_email, target_port, expires_at, last_error. Config via ssl.admin-email and ssl.target-port properties.
- **Date:** 2026-03-12

#### SslService
- **File(s):** `domains/service/SslService.java`
- **Type:** service
- **Description:** Core SSL orchestration via SSH. Provision: installs nginx+certbot, uploads reverse proxy config (with WebSocket upgrade), runs certbot --nginx, sets expiresAt=now+90d. Renew/Remove/Check commands. Best-effort pattern. removeByServerId for cleanup.
- **Dependencies:** ServerService, SshService, AuditService, SslConfig, SslCertificateRepository
- **Date:** 2026-03-12

#### SslController
- **File(s):** `domains/controller/SslController.java`
- **Type:** controller
- **Description:** REST at /api/v1/ssl-certificates. POST provision (resolves domain assignment), GET list/by-id/by-server, POST renew/check, DELETE remove (ADMIN-only).
- **Dependencies:** SslService, DomainAssignmentRepository
- **Date:** 2026-03-12

#### Modified: AuditAction, SecurityConfig, DomainAssignmentService, ServerService, servers.html
- **Description:** AuditAction: SSL_PROVISIONED/RENEWED/REMOVED/CHECK. SecurityConfig: ADMIN DELETE for ssl-certificates. DomainAssignmentService: @Lazy SslService, auto-provision after DNS creation. ServerService: @Lazy SslService, cleanup on delete, updateSslEnabled(). servers.html: SSL column with Provision/Renew/Retry buttons.
- **Date:** 2026-03-12
