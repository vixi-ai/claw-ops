# Task 34: Monitoring REST API & Controller Layer

**Status:** DONE
**Module(s):** monitoring
**Priority:** HIGH
**Created:** 2026-03-25
**Completed:** 2026-03-25

## Description

Implement all REST API endpoints for the monitoring module. Provides server health overview, metric history, alert management, incident management, monitoring configuration, and notification channel management.

## Acceptance Criteria

### Health & Metrics API
- [x] `GET /api/v1/monitoring/health` — fleet overview: all servers with current health state, sortable/filterable
- [x] `GET /api/v1/monitoring/health/{serverId}` — single server health detail with all metric values
- [x] `GET /api/v1/monitoring/metrics/{serverId}` — metric history (filterable by type, time range)
- [x] `POST /api/v1/monitoring/check/{serverId}` — trigger immediate check for a server (async)
- [x] `GET /api/v1/monitoring/metrics/{serverId}/latest` — latest metric values (no history)

### Monitoring Configuration API
- [x] `GET /api/v1/monitoring/profiles/{serverId}` — get monitoring profile for a server
- [x] `PATCH /api/v1/monitoring/profiles/{serverId}` — update thresholds, interval, enable/disable
- [x] `POST /api/v1/monitoring/profiles/{serverId}/reset` — reset to defaults

### Service Checks API (deferred to Phase 2)
- [ ] `GET /api/v1/monitoring/services/{serverId}` — list service checks for a server
- [ ] `POST /api/v1/monitoring/services/{serverId}` — add custom service check
- [ ] `PATCH /api/v1/monitoring/services/{serverId}/{checkId}` — update service check
- [ ] `DELETE /api/v1/monitoring/services/{serverId}/{checkId}` — delete service check

### Endpoint Checks API (deferred to Phase 2)
- [ ] `GET /api/v1/monitoring/endpoints` — list all endpoint checks
- [ ] `GET /api/v1/monitoring/endpoints/{serverId}` — endpoint checks for a server
- [ ] `POST /api/v1/monitoring/endpoints` — create endpoint check
- [ ] `PATCH /api/v1/monitoring/endpoints/{id}` — update endpoint check
- [ ] `DELETE /api/v1/monitoring/endpoints/{id}` — delete endpoint check
- [ ] `POST /api/v1/monitoring/endpoints/{id}/check` — trigger immediate endpoint check

### Alerts API (Phase 2)
- [ ] `GET /api/v1/monitoring/alerts` — list active alerts (filterable by severity, server, status)
- [ ] `GET /api/v1/monitoring/alerts/{id}` — alert detail
- [ ] `POST /api/v1/monitoring/alerts/{id}/acknowledge` — acknowledge alert
- [ ] `POST /api/v1/monitoring/alerts/{id}/silence` — silence for duration
- [ ] `GET /api/v1/monitoring/alert-rules` — list alert rules
- [ ] `POST /api/v1/monitoring/alert-rules` — create custom alert rule
- [ ] `PATCH /api/v1/monitoring/alert-rules/{id}` — update rule
- [ ] `DELETE /api/v1/monitoring/alert-rules/{id}` — delete rule

### Incidents API (Phase 2)
- [ ] `GET /api/v1/monitoring/incidents` — list incidents (filterable by status, severity)
- [ ] `GET /api/v1/monitoring/incidents/{id}` — incident detail with timeline
- [ ] `POST /api/v1/monitoring/incidents/{id}/acknowledge` — acknowledge
- [ ] `POST /api/v1/monitoring/incidents/{id}/assign` — assign to user
- [ ] `POST /api/v1/monitoring/incidents/{id}/notes` — add note
- [ ] `POST /api/v1/monitoring/incidents/{id}/resolve` — resolve with notes
- [ ] `POST /api/v1/monitoring/incidents/{id}/close` — close with root cause

### Notification Channels API (Phase 2)
- [ ] `GET /api/v1/monitoring/channels` — list notification channels
- [ ] `POST /api/v1/monitoring/channels` — create channel
- [ ] `PATCH /api/v1/monitoring/channels/{id}` — update channel
- [ ] `DELETE /api/v1/monitoring/channels/{id}` — delete channel
- [ ] `POST /api/v1/monitoring/channels/{id}/test` — send test notification

### Maintenance Windows API
- [x] `GET /api/v1/monitoring/maintenance` — list maintenance windows
- [x] `POST /api/v1/monitoring/maintenance` — create maintenance window
- [x] `DELETE /api/v1/monitoring/maintenance/{id}` — cancel maintenance window

## Implementation Notes

### MVP Endpoints (implement first)
1. Fleet health overview (`GET /health`)
2. Server health detail (`GET /health/{serverId}`)
3. Metric history (`GET /metrics/{serverId}`)
4. Monitoring profile CRUD
5. Trigger immediate check
6. Latest metrics

### Response Shapes
```java
// Fleet overview response
record FleetHealthResponse(
    int totalServers,
    int healthy, int warning, int critical, int unreachable, int unknown, int maintenance,
    List<ServerHealthSummary> servers
)

// Server health summary (used in fleet overview list)
record ServerHealthSummary(
    UUID serverId, String serverName, String hostname, String environment,
    HealthState overallState, HealthState cpuState, HealthState memoryState, HealthState diskState,
    Double cpuUsage, Double memoryUsage, Double diskUsage, Double load1m,
    Long uptimeSeconds, boolean sshReachable, Instant lastCheckAt
)

// Metric history response
record MetricHistoryResponse(
    UUID serverId, MetricType metricType, String label,
    List<MetricPoint> dataPoints  // [{timestamp, value}, ...]
)
```

### All controllers follow existing patterns
- `@RestController`, `@RequestMapping("/api/v1/monitoring")`
- `@Tag(name = "Monitoring")` for Swagger
- `@Valid` on request bodies
- `ResponseEntity<>` return types
- Constructor injection
- Authentication via `(UUID) authentication.getPrincipal()`

## Files Modified
- `src/main/java/.../monitoring/dto/` — 8 DTO records (FleetHealthResponse, ServerHealthSummary, MetricHistoryResponse, MetricPoint, MonitoringProfileResponse, UpdateMonitoringProfileRequest, CreateMaintenanceWindowRequest, MaintenanceWindowResponse)
- `src/main/java/.../monitoring/mapper/` — 3 mappers (HealthSnapshotMapper, MonitoringProfileMapper, MaintenanceWindowMapper)
- `src/main/java/.../monitoring/service/HealthService.java` — Fleet overview and server health detail
- `src/main/java/.../monitoring/service/MonitoringProfileService.java` — Profile CRUD with auto-create defaults
- `src/main/java/.../monitoring/service/MaintenanceService.java` — Maintenance window management
- `src/main/java/.../monitoring/controller/MonitoringController.java` — All MVP REST endpoints
