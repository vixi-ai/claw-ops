# Task 14: Auto-Import Domains on Provider Account Creation + Frontend Simplification

**Status:** DONE
**Module(s):** domains, servers
**Priority:** HIGH
**Created:** 2026-03-12
**Completed:** 2026-03-12

## Description
When adding a provider account, automatically discover and import all domains from that provider. Simplify the domains frontend from 3 tabs to 2 sections with auto-imported domains shown under provider accounts.

## Acceptance Criteria
- [x] `listDomains()` method added to `DnsProviderAdapter` interface
- [x] Cloudflare adapter implements `listDomains()` via paginated `GET /zones`
- [x] Namecheap adapter implements `listDomains()` via paginated `domains.getList`
- [x] `syncDomainsForAccount()` in `ProviderAccountService` creates `ManagedZone` for each discovered domain
- [x] Auto-sync triggered after successful credential validation
- [x] `POST /provider-accounts/{id}/sync-domains` endpoint for manual re-sync
- [x] Frontend redesigned: 2 sections (Accounts+Domains, Subdomains) instead of 3 tabs
- [x] Domains shown as expandable rows under provider accounts
- [x] "Sync" button per account to refresh domain list
- [x] Zone filter on subdomains section
- [x] Build succeeds

## Implementation Notes
- `DiscoveredDomain` record: `domainName`, `providerZoneId`, `manageable`
- Cloudflare: zone status == "active" means manageable
- Namecheap: `IsOurDNS` attribute determines manageability
- Sync is idempotent — existing zones are skipped (matched by zoneName + providerAccountId)
- Auto-sync on validate means: add account → validate → domains appear automatically
- Frontend uses `Promise.all` to load accounts + zones in parallel, groups zones by accountId

## Files Modified
- `domains/provider/DnsProviderAdapter.java` — added `listDomains()` method
- `domains/provider/DiscoveredDomain.java` — NEW record
- `domains/provider/cloudflare/CloudflareAdapter.java` — `listDomains()` implementation
- `domains/provider/namecheap/NamecheapAdapter.java` — `listDomains()` implementation
- `domains/dto/SyncDomainsResponse.java` — NEW DTO
- `domains/service/ProviderAccountService.java` — `syncDomainsForAccount()`, auto-sync on validate
- `domains/controller/ProviderAccountController.java` — `POST /{id}/sync-domains` endpoint
- `src/main/resources/static/dev/domains.html` — full redesign: 2-section layout
