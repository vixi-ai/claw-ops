# Task 13: Wire Domain Auto-Assignment into Server Creation API

**Status:** DONE
**Module(s):** servers, domains
**Priority:** HIGH
**Created:** 2026-03-12
**Completed:** 2026-03-12

## Description

Integrate domain auto-assignment into the server creation API as a single atomic operation. Previously, the frontend made two separate API calls (POST /servers + POST /domain-assignments/server) which was fragile and caused a double-assignment bug. Now the user picks a zone in the server form, and `POST /servers` handles both server creation and domain assignment in one transaction.

## Acceptance Criteria

- [x] `CreateServerRequest` has optional `UUID zoneId` field
- [x] `ServerResponse` includes `String assignedDomain` (computed from subdomain + rootDomain)
- [x] `DomainAssignmentService.autoAssignServerDomain()` accepts explicit zoneId (5-arg overload)
- [x] `ServerService.createServer()` passes zoneId, populates rootDomain/subdomain from assignment result
- [x] When zoneId is explicitly provided and fails, entire transaction rolls back (server not created)
- [x] When zoneId is null, falls back to default auto-assign zone (best-effort, server always created)
- [x] Frontend sends zoneId in POST /servers body, no second API call
- [x] Server table shows Domain column
- [x] Fixed: ipAddress auto-detected from host field when it's an IP
- [x] Fixed: Edit flow doesn't re-create credentials when user didn't change them
- [x] Fixed: Double-assignment bug eliminated (single code path)

## Files Modified

- `servers/dto/CreateServerRequest.java` — added `UUID zoneId` field
- `servers/dto/ServerResponse.java` — added `String assignedDomain` field
- `servers/mapper/ServerMapper.java` — computed assignedDomain in toResponse()
- `servers/service/ServerService.java` — pass zoneId, populate legacy fields, error handling for explicit vs default zone
- `domains/service/DomainAssignmentService.java` — added 5-arg autoAssignServerDomain overload, extracted doAutoAssign private method
- `src/main/resources/static/dev/servers.html` — single-call flow, Domain column, ipAddress fix, edit credential fix
