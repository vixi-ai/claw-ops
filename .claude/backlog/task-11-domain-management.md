# Task 11: Provider-Agnostic Domain Management Module

**Status:** DONE
**Module(s):** domains, common, auth, audit
**Priority:** HIGH
**Created:** 2026-03-12
**Completed:** 2026-03-12

## Description
Build a provider-agnostic DNS domain management module with four core concepts: ProviderAccount, ManagedZone, DomainAssignment, DomainEvent. Supports multiple DNS providers (Cloudflare first), multiple zones, intent-based APIs, and desired-state tracking. See plan file for full details.

## Acceptance Criteria
- [x] V9 migration creates 4 tables with indexes
- [x] 7 enums, 4 entities, 12 DTOs, 4 mappers, 4 repositories
- [x] DnsProviderAdapter interface + ProviderCapabilities + ProviderAdapterFactory
- [x] CloudflareAdapter (first provider) using RestClient
- [x] HostnameStrategy abstraction with SlugBasedHostnameStrategy
- [x] 4 services: DomainEventService, ProviderAccountService, ManagedZoneService, DomainAssignmentService
- [x] 3 controllers: ProviderAccountController, ManagedZoneController, DomainAssignmentController
- [x] DomainException (422) + DnsProviderException (502)
- [x] 9 new AuditAction values
- [x] SecurityConfig updated for domain endpoints
- [x] Functional domains.html dev page with 3 tabs
- [x] Module spec + architecture log updated

## Files Modified
**New files (~40):**
- `src/main/resources/db/migration/V9__create_domain_tables.sql`
- `domains/entity/` — DnsProviderType, AssignmentType, AssignmentStatus, DnsRecordType, DomainEventAction, DomainEventOutcome, HealthStatus, ProviderAccount, ManagedZone, DomainAssignment, DomainEvent
- `domains/dto/` — CreateProviderAccountRequest, UpdateProviderAccountRequest, ProviderAccountResponse, CreateManagedZoneRequest, UpdateManagedZoneRequest, ManagedZoneResponse, AssignServerDomainRequest, AssignCustomDomainRequest, DomainAssignmentResponse, DomainEventResponse, ValidateCredentialsResponse, VerifyZoneResponse
- `domains/mapper/` — ProviderAccountMapper, ManagedZoneMapper, DomainAssignmentMapper, DomainEventMapper
- `domains/repository/` — ProviderAccountRepository, ManagedZoneRepository, DomainAssignmentRepository, DomainEventRepository
- `domains/exception/` — DomainException, DnsProviderException
- `domains/provider/` — DnsProviderAdapter, ProviderCapabilities, DnsRecord, ProviderAdapterFactory
- `domains/provider/cloudflare/` — CloudflareAdapter, CloudflareModels
- `domains/naming/` — HostnameStrategy, SlugBasedHostnameStrategy, HostnameStrategyConfig
- `domains/service/` — DomainEventService, ProviderAccountService, ManagedZoneService, DomainAssignmentService
- `domains/controller/` — ProviderAccountController, ManagedZoneController, DomainAssignmentController

**Modified files:**
- `audit/entity/AuditAction.java` — added 9 domain audit actions
- `common/exception/GlobalExceptionHandler.java` — added DomainException (422) and DnsProviderException (502) handlers
- `auth/config/SecurityConfig.java` — added ADMIN-only DELETE for domain endpoints
- `src/main/resources/static/dev/domains.html` — replaced placeholder with functional 3-tab page
- `src/main/resources/static/dev/index.html` — updated Domains card to Active
- `.claude/modules/domains.md` — rewritten for new four-entity design
- `.claude/architecture/domains.md` — all new component entries
