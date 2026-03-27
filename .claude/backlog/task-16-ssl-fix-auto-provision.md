# Task 16: SSL Provisioning Fix + Auto-SSL on Server Creation

**Status:** DONE
**Module(s):** domains, servers
**Priority:** HIGH
**Created:** 2026-03-15
**Completed:** 2026-03-15

## Description

SSL provisioning (Task 15) is implemented but broken in production. When attempting to provision SSL via `servers.html`, nginx fails to start because port 80 is already in use on the target server. Additionally, SSL must be manually triggered — it should auto-provision when a server is created with a domain. Existing servers with domains but no SSL have no way to bulk-provision.

## Error

```
Nginx failed to start: bind() to 0.0.0.0:80 failed (98: Address already in use)
```

## Root Causes

1. **Port 80 conflict** — `systemctl restart nginx` runs without checking if something else holds port 80 (Apache, old nginx PID, Docker container, another HTTP service).
2. **No auto-provision on server creation** — SSL must be triggered manually from the UI after adding a server with a domain.
3. **No backfill path** — servers already in DB with domains but no SSL have no way to get bulk-provisioned.

## Acceptance Criteria

- [ ] Provisioning SSL on a server where Apache holds port 80 succeeds (Apache is stopped, nginx takes over)
- [ ] Provisioning SSL on a server where an unknown process holds port 80 gets a clear error message instead of cryptic bind failure
- [ ] Adding a new server with a domain auto-triggers SSL provisioning (non-blocking — server creation succeeds even if SSL fails)
- [ ] `POST /api/v1/ssl-certificates/provision-all` provisions SSL for all servers with domains that don't have an active cert
- [ ] "Provision All SSL" button appears in servers.html next to "+ New Server"
- [ ] Build passes: `./mvnw clean install -DskipTests`

## Implementation Notes

### Fix 1: Port 80 pre-flight in SslService.java

Before `systemctl restart nginx`, upload and execute a shell script `/tmp/free-port-80.sh`:

```bash
#!/bin/bash
# Stop Apache if present
if systemctl is-active --quiet apache2 2>/dev/null; then
  systemctl stop apache2 && systemctl disable apache2
fi
# Kill anything else on port 80
if ss -tlnp 'sport = :80' | grep -q LISTEN; then
  fuser -k 80/tcp || true
  sleep 2
fi
# Final check
if ss -tlnp 'sport = :80' | grep -q LISTEN; then
  echo "PORT_BUSY"; exit 1
fi
echo "PORT_FREE"; exit 0
```

Check stdout for `PORT_BUSY` → throw `DomainException("Port 80 is occupied by an unmanaged process. Free port 80 on the server before provisioning SSL.")`

Also: check if nginx is already active before restart — use `systemctl reload` instead of `restart` if it is.

### Fix 2: Auto-provision SSL on domain assignment

In `DomainAssignmentService.doAutoAssign()`, after DNS record is created and assignment is saved:

```java
try {
    sslService.provision(serverId, null, fullHostname, null, userId);
} catch (Exception e) {
    log.warn("Auto SSL provisioning skipped for {}: {}", fullHostname, e.getMessage());
}
```

Inject `SslService` with `@Lazy` in `DomainAssignmentService` constructor to avoid circular dependency.

### Fix 3: Bulk provision endpoint

New method `SslService.provisionMissingForAll(UUID userId)`:
1. Load all servers where `subdomain IS NOT NULL AND root_domain IS NOT NULL` (via `ServerRepository`)
2. For each, check `SslCertificateRepository.findByServerId()` — skip if ACTIVE or PROVISIONING
3. Call `provision()` for others
4. Return `BulkSslProvisionResponse(total, provisioned, skipped, failed)`

New endpoint: `POST /api/v1/ssl-certificates/provision-all` (ADMIN role)

New DTO: `domains/dto/BulkSslProvisionResponse.java`

### Fix 4: Frontend button

In `servers.html` header row, add next to "+ New Server":
```html
<button class="btn" onclick="provisionAllSsl(this)">Provision All SSL</button>
```

Function calls `POST /api/v1/ssl-certificates/provision-all`, shows result summary, reloads table.

## Files to Modify

| File | Change |
|------|--------|
| `domains/service/SslService.java` | Port-80 pre-flight, reload vs restart, `provisionMissingForAll()` |
| `domains/service/DomainAssignmentService.java` | Auto-SSL after domain assignment |
| `domains/controller/SslController.java` | `POST /provision-all` endpoint |
| `domains/dto/BulkSslProvisionResponse.java` | NEW |
| `src/main/resources/static/dev/servers.html` | "Provision All SSL" button |

## Files Modified
- `domains/service/SslService.java` — Added `FREE_PORT_80_SCRIPT` constant; port-80 pre-flight before nginx start; reload vs start logic; `provisionMissingForAll()`; `ServerRepository` injection
- `domains/controller/SslController.java` — Added `POST /provision-all` endpoint
- `domains/dto/BulkSslProvisionResponse.java` — NEW
- `servers/repository/ServerRepository.java` — Added `findBySubdomainIsNotNull()`
- `src/main/resources/static/dev/servers.html` — Added "Provision All SSL" button + `provisionAllSsl()` function
