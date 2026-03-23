# Task 12: Automatic Subdomain Provisioning on Server Creation

**Status:** DONE
**Module(s):** domains, servers
**Priority:** HIGH
**Created:** 2026-03-12
**Completed:** 2026-03-12

## Description

When a new server is added to the system, automatically create a DNS subdomain record pointing to that server's IP address. The subdomain is derived from the server name using the existing `SlugBasedHostnameStrategy`. If a subdomain with that name already exists, append a numeric suffix to make it unique (e.g., `my-server-2.example.com`). When a server is deleted, its associated domain assignment(s) should be automatically released (DNS record removed).

Subdomain management (list, delete, verify) already exists via `DomainAssignmentController`. This task adds the **automatic trigger** on server lifecycle events and the **uniqueness resolution** logic.

## Design Decisions

### Which zone gets the subdomain?

Introduce a `defaultForAutoAssign` boolean on `ManagedZone`. Only ONE zone can be the auto-assign target at a time. When activating a zone, the admin can mark it as the default. If no zone is marked as default, auto-assignment is silently skipped (no error — the server is still created).

This avoids ambiguity when multiple zones exist (e.g., `prod.example.com` and `staging.example.com`).

### Hostname uniqueness resolution

1. Generate base hostname: `slugify(serverName) + "." + zoneName` → e.g., `my-server.example.com`
2. Check if that hostname is already assigned (status != RELEASED)
3. If taken, try `my-server-2.example.com`, then `my-server-3.example.com`, up to `-99`
4. If all 99 are taken, skip auto-assignment and log a warning

### Failure handling

- DNS provisioning failure must NOT fail server creation. The server is created regardless.
- On failure, the domain assignment is saved with status `FAILED` so admins can see it and retry.
- A warning is logged but no exception propagates to the caller.

### Server deletion

- When a server is deleted, call `DomainAssignmentService.releaseAllForResource(serverId, userId)` to remove all associated DNS records.
- This already exists but is not wired into server deletion flow.

## Acceptance Criteria

- [x] New DB migration adds `default_for_auto_assign` column to `managed_zones`
- [x] `ManagedZone` entity updated with `defaultForAutoAssign` field
- [x] Only one zone can be `defaultForAutoAssign=true` at a time (enforced in service layer + DB partial unique index)
- [x] `ManagedZoneService` has `setDefaultForAutoAssign(UUID zoneId)` and `getDefaultAutoAssignZone()` methods
- [x] `ManagedZoneController` has `POST /api/v1/zones/{id}/set-default` endpoint
- [x] `ManagedZoneResponse` DTO includes `defaultForAutoAssign` field
- [x] Hostname uniqueness resolution implemented in `DomainAssignmentService.resolveUniqueHostname()` (kept in service rather than strategy interface — simpler, avoids changing HostnameStrategy contract)
- [x] `DomainAssignmentService` gets `autoAssignServerDomain(UUID serverId, String serverName, String serverIp, UUID userId)` method
- [x] `ServerService.createServer()` calls auto-assign after successful server save (fire-and-forget, no failure propagation)
- [x] `ServerService.deleteServer()` calls `releaseAllForResource(serverId, userId)` before deleting the server
- [x] Auto-assignment is skipped silently if no default zone exists or zone is not active
- [x] Hostname collision appends `-2`, `-3`, etc. up to `-99`
- [x] Domain assignments tab — auto-assigned and manual both show as `SERVER` assignment type (same data, differentiated by audit log)
- [x] `domains.html` Managed Zones table shows a "Default" badge and a "Set Default" button
- [x] Audit log records auto-assignment events with action `DOMAIN_AUTO_ASSIGNED`

## Implementation Plan

### Step 1: DB Migration V10

**File:** `src/main/resources/db/migration/V10__add_auto_assign_to_zones.sql`

```sql
ALTER TABLE managed_zones ADD COLUMN default_for_auto_assign BOOLEAN NOT NULL DEFAULT FALSE;

-- Partial unique index: only one zone can be the default
CREATE UNIQUE INDEX idx_managed_zones_auto_assign_default
    ON managed_zones (default_for_auto_assign)
    WHERE default_for_auto_assign = TRUE;
```

### Step 2: Update ManagedZone Entity

**File:** `domains/entity/ManagedZone.java`

- Add `private boolean defaultForAutoAssign = false;` field with getter/setter

### Step 3: Update ManagedZone DTOs

- `ManagedZoneResponse` — add `boolean defaultForAutoAssign`
- `UpdateManagedZoneRequest` — add `Boolean defaultForAutoAssign` (optional)
- `ManagedZoneMapper.toResponse()` — map the new field

### Step 4: Update ManagedZoneRepository

- Add `Optional<ManagedZone> findByDefaultForAutoAssignTrue()`
- Add `@Modifying @Query("UPDATE ManagedZone z SET z.defaultForAutoAssign = false WHERE z.defaultForAutoAssign = true")` method `clearDefaultAutoAssign()`

### Step 5: Update ManagedZoneService

Add two methods:

```java
@Transactional
public ManagedZoneResponse setDefaultForAutoAssign(UUID zoneId, UUID userId) {
    // 1. Validate zone exists and is active
    // 2. Clear any existing default: clearDefaultAutoAssign()
    // 3. Set this zone as default
    // 4. Audit log
    // 5. Return updated response
}

public Optional<ManagedZone> getDefaultAutoAssignZone() {
    return managedZoneRepository.findByDefaultForAutoAssignTrue();
}
```

### Step 6: Update ManagedZoneController

- Add `POST /api/v1/zones/{id}/set-default` → calls `setDefaultForAutoAssign()`

### Step 7: Hostname Uniqueness in DomainAssignmentService

Add method:

```java
/**
 * Generates a unique hostname for a server within the given zone.
 * If "my-server.zone.com" is taken, tries "my-server-2.zone.com", etc.
 */
private String resolveUniqueHostname(String serverName, String zoneName) {
    String base = hostnameStrategy.generateServerHostname(serverName, zoneName);
    if (isHostnameAvailable(base)) return base;

    String slug = slugify(serverName); // extract slug part
    for (int i = 2; i <= 99; i++) {
        String candidate = slug + "-" + i + "." + zoneName;
        if (isHostnameAvailable(candidate)) return candidate;
    }
    return null; // all taken
}

private boolean isHostnameAvailable(String hostname) {
    return domainAssignmentRepository
        .findByHostnameAndStatusNot(hostname, AssignmentStatus.RELEASED)
        .isEmpty();
}
```

### Step 8: Auto-Assign Method in DomainAssignmentService

Add public method:

```java
/**
 * Automatically provisions a subdomain for a newly created server.
 * Returns the assignment if successful, empty if skipped/failed.
 * Never throws — failures are logged and recorded as FAILED assignments.
 */
public Optional<DomainAssignmentResponse> autoAssignServerDomain(
        UUID serverId, String serverName, String serverIp, UUID userId) {
    try {
        // 1. Find default auto-assign zone (skip if none)
        Optional<ManagedZone> zoneOpt = managedZoneService.getDefaultAutoAssignZone();
        if (zoneOpt.isEmpty()) {
            log.debug("No default auto-assign zone configured, skipping for server '{}'", serverName);
            return Optional.empty();
        }
        ManagedZone zone = zoneOpt.get();

        // 2. Resolve unique hostname
        String hostname = resolveUniqueHostname(serverName, zone.getZoneName());
        if (hostname == null) {
            log.warn("Could not generate unique hostname for server '{}' in zone '{}'",
                     serverName, zone.getZoneName());
            return Optional.empty();
        }

        // 3. Create assignment (reuse existing provisioning logic)
        // ... create DomainAssignment entity, call provider adapter, etc.
        // Same flow as assignServerDomain() but with the resolved hostname

        // 4. Audit with DOMAIN_AUTO_ASSIGNED action
    } catch (Exception e) {
        log.error("Auto-assign domain failed for server '{}': {}", serverName, e.getMessage());
        return Optional.empty();
    }
}
```

### Step 9: Wire into ServerService

**File:** `servers/service/ServerService.java`

Inject `DomainAssignmentService` into `ServerService`.

In `createServer()`, after successful save and audit log:

```java
// Auto-assign subdomain (best-effort, never fails server creation)
try {
    String ip = saved.getIpAddress() != null ? saved.getIpAddress() : saved.getHostname();
    domainAssignmentService.autoAssignServerDomain(
            saved.getId(), saved.getName(), ip, currentUserId);
} catch (Exception e) {
    log.warn("Auto-assign domain failed for server '{}': {}", saved.getName(), e.getMessage());
}
```

In `deleteServer()`, before deleting:

```java
// Release all domain assignments for this server
try {
    domainAssignmentService.releaseAllForResource(serverId, currentUserId);
} catch (Exception e) {
    log.warn("Failed to release domains for server {}: {}", serverId, e.getMessage());
}
```

### Step 10: Add AuditAction

- Add `DOMAIN_AUTO_ASSIGNED` to `AuditAction.java`

### Step 11: Update domains.html

**Managed Zones table:**
- Add "Default" column — shows a green badge if `defaultForAutoAssign` is true
- Add "Set Default" button per row (only on active zones) — calls `POST /zones/{id}/set-default`

**Domain Assignments table:**
- The `assignmentType` column already distinguishes SERVER vs CUSTOM
- No additional changes needed — auto-assigned domains show as `SERVER` type

### Step 12: Update servers.html (optional enhancement)

- In the server details/table, show the auto-assigned domain hostname if one exists
- Small badge or link next to the server name showing its subdomain

## Dependencies

- `DomainAssignmentService` (already exists — needs new `autoAssignServerDomain` method)
- `ManagedZoneService` (already exists — needs `setDefaultForAutoAssign` and `getDefaultAutoAssignZone`)
- `ServerService` (already exists — needs to call domain auto-assign on create/delete)
- `HostnameStrategy` / `SlugBasedHostnameStrategy` (already exists — used as-is)
- `ProviderAdapterFactory` + adapters (already exist — used by DomainAssignmentService)

## Cross-Module Communication

```
ServerService.createServer()
    → DomainAssignmentService.autoAssignServerDomain()
        → ManagedZoneService.getDefaultAutoAssignZone()
        → HostnameStrategy.generateServerHostname()
        → DomainAssignmentRepository (uniqueness check)
        → ProviderAdapterFactory.getAdapter()
        → DnsProviderAdapter.createOrUpsertRecord()
        → DomainEventService.recordEvent()
        → AuditService.log()

ServerService.deleteServer()
    → DomainAssignmentService.releaseAllForResource()
        → DnsProviderAdapter.deleteRecord()
```

## Files Modified

- `src/main/resources/db/migration/V10__add_auto_assign_to_zones.sql` (NEW)
- `domains/entity/ManagedZone.java` (modified — added defaultForAutoAssign field)
- `domains/dto/ManagedZoneResponse.java` (modified — added defaultForAutoAssign)
- `domains/mapper/ManagedZoneMapper.java` (modified — maps defaultForAutoAssign)
- `domains/repository/ManagedZoneRepository.java` (modified — findByDefaultForAutoAssignTrue, clearDefaultAutoAssign)
- `domains/service/ManagedZoneService.java` (modified — setDefaultForAutoAssign, getDefaultAutoAssignZone)
- `domains/controller/ManagedZoneController.java` (modified — POST /{id}/set-default)
- `domains/service/DomainAssignmentService.java` (modified — autoAssignServerDomain, resolveUniqueHostname, helpers)
- `servers/service/ServerService.java` (modified — auto-assign on create, release on delete, @Lazy DomainAssignmentService)
- `audit/entity/AuditAction.java` (modified — added DOMAIN_AUTO_ASSIGNED)
- `src/main/resources/static/dev/domains.html` (modified — Default column + Set Default button)

## Notes

- The `defaultForAutoAssign` flag is enforced at DB level (partial unique index) AND service level (clear-then-set pattern)
- Auto-assignment is completely fire-and-forget — server creation is the primary operation and must never fail due to DNS issues
- The existing `assignServerDomain()` (manual) and `autoAssignServerDomain()` share logic but the auto method adds uniqueness resolution and swallows exceptions
- Circular dependency risk: `ServerService` → `DomainAssignmentService` → `ServerService` (for server lookup). Avoid by passing server data directly (name, IP, ID) instead of having DomainAssignmentService call back into ServerService during auto-assign
