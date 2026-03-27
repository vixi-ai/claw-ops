# Monitoring Module

## Purpose

Provides continuous health monitoring, metrics collection, alerting, and incident management for managed servers. Collects infrastructure metrics via SSH (no agents required), evaluates health state, triggers alerts based on configurable rules, and manages incident lifecycle. Transforms ClawOps from a server management tool into a full infrastructure observability platform.

## Package

`com.openclaw.manager.openclawserversmanager.monitoring`

## Components

### Entity: `MetricSample`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| serverId | UUID | FK → Server, NOT NULL, indexed |
| metricType | MetricType (enum) | NOT NULL |
| value | double | NOT NULL |
| unit | String | nullable (e.g., "percent", "bytes", "seconds") |
| label | String | nullable (e.g., mount point "/data", interface "eth0") |
| collectedAt | Instant | NOT NULL, indexed |

### Entity: `HealthSnapshot`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| serverId | UUID | FK → Server, UNIQUE active per server, NOT NULL, indexed |
| state | HealthState (enum) | NOT NULL |
| previousState | HealthState (enum) | nullable |
| cpuUsage | double | nullable |
| memoryUsage | double | nullable |
| diskUsage | double | nullable |
| load1m | double | nullable |
| uptimeSeconds | long | nullable |
| summary | String | nullable, human-readable health summary |
| stateChangedAt | Instant | nullable — when state last transitioned |
| evaluatedAt | Instant | NOT NULL |

### Entity: `ServiceCheck`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| serverId | UUID | FK → Server, NOT NULL |
| serviceName | String | NOT NULL (e.g., "nginx", "postgresql") |
| serviceType | ServiceType (enum) | NOT NULL |
| isRunning | boolean | NOT NULL |
| pid | Integer | nullable |
| memoryUsageBytes | Long | nullable |
| cpuUsagePercent | Double | nullable |
| details | String | nullable, JSON extra info |
| checkedAt | Instant | NOT NULL |

### Entity: `EndpointCheck`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| serverId | UUID | FK → Server, nullable (can be standalone) |
| name | String | NOT NULL |
| url | String | NOT NULL |
| checkType | EndpointCheckType (enum) | NOT NULL |
| expectedStatusCode | Integer | nullable, default 200 |
| isUp | boolean | NOT NULL |
| responseTimeMs | Long | nullable |
| statusCode | Integer | nullable |
| sslExpiresAt | Instant | nullable |
| sslDaysRemaining | Integer | nullable |
| errorMessage | String | nullable |
| checkedAt | Instant | NOT NULL |

### Entity: `AlertRule`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| name | String | NOT NULL, UNIQUE |
| description | String | nullable |
| serverId | UUID | FK → Server, nullable (null = applies to all servers) |
| metricType | MetricType (enum) | nullable (for metric-based rules) |
| conditionOperator | ConditionOperator (enum) | NOT NULL |
| thresholdValue | double | NOT NULL |
| severity | AlertSeverity (enum) | NOT NULL |
| consecutiveFailures | int | NOT NULL, default 3 |
| cooldownMinutes | int | NOT NULL, default 15 |
| enabled | boolean | NOT NULL, default true |
| notificationChannelIds | List<UUID> | nullable |
| createdAt | Instant | auto-set |
| updatedAt | Instant | auto-set |

### Entity: `AlertEvent`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| alertRuleId | UUID | FK → AlertRule, NOT NULL |
| serverId | UUID | FK → Server, NOT NULL |
| severity | AlertSeverity (enum) | NOT NULL |
| status | AlertEventStatus (enum) | NOT NULL |
| metricType | MetricType (enum) | nullable |
| metricValue | Double | nullable |
| message | String | NOT NULL |
| acknowledgedBy | UUID | FK → User, nullable |
| acknowledgedAt | Instant | nullable |
| resolvedAt | Instant | nullable |
| incidentId | UUID | FK → Incident, nullable |
| firedAt | Instant | NOT NULL |

### Entity: `Incident`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| title | String | NOT NULL |
| description | String | nullable |
| serverId | UUID | FK → Server, NOT NULL |
| severity | AlertSeverity (enum) | NOT NULL |
| status | IncidentStatus (enum) | NOT NULL |
| openedAt | Instant | NOT NULL |
| acknowledgedAt | Instant | nullable |
| resolvedAt | Instant | nullable |
| closedAt | Instant | nullable |
| resolvedBy | UUID | FK → User, nullable |
| rootCause | String | nullable |

### Entity: `IncidentNote`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| incidentId | UUID | FK → Incident, NOT NULL |
| authorId | UUID | FK → User, NOT NULL |
| content | String | NOT NULL |
| createdAt | Instant | auto-set |

### Entity: `NotificationChannel`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| name | String | NOT NULL, UNIQUE |
| type | ChannelType (enum) | NOT NULL |
| config | String | NOT NULL, JSON encrypted (webhook URL, email, token, etc.) |
| enabled | boolean | NOT NULL, default true |
| createdAt | Instant | auto-set |
| updatedAt | Instant | auto-set |

### Entity: `MaintenanceWindow`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| serverId | UUID | FK → Server, NOT NULL |
| reason | String | NOT NULL |
| startAt | Instant | NOT NULL |
| endAt | Instant | NOT NULL |
| createdBy | UUID | FK → User, NOT NULL |
| createdAt | Instant | auto-set |

### Enums

**`MetricType`**: `CPU_USAGE_PERCENT`, `MEMORY_USAGE_PERCENT`, `MEMORY_USED_BYTES`, `MEMORY_TOTAL_BYTES`, `DISK_USAGE_PERCENT`, `DISK_USED_BYTES`, `DISK_TOTAL_BYTES`, `LOAD_1M`, `LOAD_5M`, `LOAD_15M`, `UPTIME_SECONDS`, `PROCESS_COUNT`, `SWAP_USAGE_PERCENT`, `NETWORK_RX_BYTES`, `NETWORK_TX_BYTES`

**`HealthState`**: `HEALTHY`, `WARNING`, `CRITICAL`, `UNREACHABLE`, `UNKNOWN`, `MAINTENANCE`

**`ServiceType`**: `SYSTEMD`, `DOCKER`, `PROCESS`

**`EndpointCheckType`**: `HTTP`, `HTTPS`, `TCP`, `SSL_CERT`, `DNS`

**`ConditionOperator`**: `GREATER_THAN`, `LESS_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL`, `EQUAL`, `NOT_EQUAL`

**`AlertSeverity`**: `INFO`, `WARNING`, `CRITICAL`

**`AlertEventStatus`**: `FIRING`, `ACKNOWLEDGED`, `RESOLVED`

**`IncidentStatus`**: `OPEN`, `ACKNOWLEDGED`, `INVESTIGATING`, `RESOLVED`, `CLOSED`

**`ChannelType`**: `EMAIL`, `SLACK`, `DISCORD`, `TELEGRAM`, `WEBHOOK`

### DTOs

**`MetricSampleResponse`**
- `id`, `serverId`, `metricType`, `value`, `unit`, `label`, `collectedAt`

**`MetricQueryRequest`**
- `serverId` — `@NotNull`
- `metricType` — `@NotNull`
- `from` — `@NotNull` Instant
- `to` — `@NotNull` Instant
- `label` — optional filter (e.g., specific mount point)

**`HealthSnapshotResponse`**
- All `HealthSnapshot` fields + `serverName`

**`ServiceCheckResponse`**
- All `ServiceCheck` fields + `serverName`

**`CreateEndpointCheckRequest`**
- `name` — `@NotBlank @Size(max = 100)`
- `url` — `@NotBlank @Pattern(regexp = valid URL)`
- `serverId` — optional UUID
- `checkType` — `@NotNull`
- `expectedStatusCode` — optional `@Min(100) @Max(599)`

**`EndpointCheckResponse`**
- All `EndpointCheck` fields

**`CreateAlertRuleRequest`**
- `name` — `@NotBlank @Size(max = 100)`
- `description` — optional `@Size(max = 500)`
- `serverId` — optional UUID (null = global rule)
- `metricType` — optional (required for metric-based rules)
- `conditionOperator` — `@NotNull`
- `thresholdValue` — `@NotNull`
- `severity` — `@NotNull`
- `consecutiveFailures` — optional `@Min(1) @Max(100)`, default 3
- `cooldownMinutes` — optional `@Min(1) @Max(1440)`, default 15
- `notificationChannelIds` — optional list of UUIDs

**`UpdateAlertRuleRequest`**
- All fields optional (partial update via PATCH)

**`AlertRuleResponse`**
- All `AlertRule` fields + `notificationChannelNames`

**`AlertEventResponse`**
- All `AlertEvent` fields + `alertRuleName`, `serverName`

**`CreateIncidentRequest`**
- `title` — `@NotBlank @Size(max = 200)`
- `description` — optional `@Size(max = 2000)`
- `serverId` — `@NotNull`
- `severity` — `@NotNull`

**`UpdateIncidentRequest`**
- `status` — optional
- `rootCause` — optional `@Size(max = 2000)`

**`IncidentResponse`**
- All `Incident` fields + `serverName`, `resolvedByUsername`, `alertEvents` count, `notes` count

**`AddIncidentNoteRequest`**
- `content` — `@NotBlank @Size(max = 5000)`

**`IncidentNoteResponse`**
- All `IncidentNote` fields + `authorUsername`

**`CreateNotificationChannelRequest`**
- `name` — `@NotBlank @Size(max = 100)`
- `type` — `@NotNull`
- `config` — `@NotBlank` JSON string (validated per channel type)

**`UpdateNotificationChannelRequest`**
- All fields optional (partial update via PATCH)

**`NotificationChannelResponse`**
- `id`, `name`, `type`, `enabled`, `createdAt`, `updatedAt` — **never return config (contains secrets)**

**`CreateMaintenanceWindowRequest`**
- `serverId` — `@NotNull`
- `reason` — `@NotBlank @Size(max = 500)`
- `startAt` — `@NotNull @Future`
- `endAt` — `@NotNull @Future`

**`MaintenanceWindowResponse`**
- All `MaintenanceWindow` fields + `serverName`, `createdByUsername`

**`MonitoringDashboardResponse`**
- `totalServers` — int
- `healthyCount`, `warningCount`, `criticalCount`, `unreachableCount`, `unknownCount`, `maintenanceCount` — int
- `activeAlerts` — int
- `openIncidents` — int
- `serverHealthSnapshots` — List<HealthSnapshotResponse>

### Service: `MetricsService`

- `recordMetrics(UUID serverId, List<MetricSample>)` — bulk insert metric samples
- `queryMetrics(MetricQueryRequest)` → `List<MetricSampleResponse>` — time-range query
- `getLatestMetrics(UUID serverId)` → `List<MetricSampleResponse>` — most recent sample per metric type
- `deleteOldMetrics(Instant before)` — retention cleanup

### Service: `HealthService`

- `evaluateHealth(UUID serverId, List<MetricSample>)` → `HealthSnapshot` — runs health evaluation logic
- `getHealthSnapshot(UUID serverId)` → `HealthSnapshotResponse` — latest snapshot
- `getAllHealthSnapshots()` → `List<HealthSnapshotResponse>` — all servers
- `getDashboard()` → `MonitoringDashboardResponse` — aggregated overview

### Service: `AlertService`

- `createAlertRule(CreateAlertRuleRequest)` → `AlertRuleResponse`
- `updateAlertRule(UUID, UpdateAlertRuleRequest)` → `AlertRuleResponse`
- `deleteAlertRule(UUID)` — removes rule
- `getAlertRules(Pageable)` → `Page<AlertRuleResponse>`
- `getAlertRule(UUID)` → `AlertRuleResponse`
- `getAlertEvents(UUID serverId, Pageable)` → `Page<AlertEventResponse>`
- `acknowledgeAlert(UUID alertEventId)` — marks alert acknowledged
- `resolveAlert(UUID alertEventId)` — marks alert resolved

### Service: `IncidentService`

- `createIncident(CreateIncidentRequest)` → `IncidentResponse`
- `updateIncident(UUID, UpdateIncidentRequest)` → `IncidentResponse`
- `getIncident(UUID)` → `IncidentResponse`
- `getIncidents(Pageable)` → `Page<IncidentResponse>`
- `getIncidentsByServer(UUID serverId, Pageable)` → `Page<IncidentResponse>`
- `addNote(UUID incidentId, AddIncidentNoteRequest)` → `IncidentNoteResponse`
- `getNotes(UUID incidentId)` → `List<IncidentNoteResponse>`

### Service: `NotificationService`

- `createChannel(CreateNotificationChannelRequest)` → `NotificationChannelResponse`
- `updateChannel(UUID, UpdateNotificationChannelRequest)` → `NotificationChannelResponse`
- `deleteChannel(UUID)` — removes channel
- `getChannels()` → `List<NotificationChannelResponse>`
- `testChannel(UUID)` — sends a test notification
- `sendAlert(AlertEvent, List<NotificationChannel>)` — dispatches alert to channels

### Service: `MaintenanceService`

- `createWindow(CreateMaintenanceWindowRequest)` → `MaintenanceWindowResponse`
- `deleteWindow(UUID)` — cancels maintenance window
- `getActiveWindows()` → `List<MaintenanceWindowResponse>`
- `getWindowsByServer(UUID serverId)` → `List<MaintenanceWindowResponse>`
- `isInMaintenance(UUID serverId)` → `boolean`

### Service: `ServiceCheckService`

- `checkServices(UUID serverId, List<String> serviceNames)` → `List<ServiceCheckResponse>`
- `getLatestServiceChecks(UUID serverId)` → `List<ServiceCheckResponse>`

### Service: `EndpointCheckService`

- `createEndpointCheck(CreateEndpointCheckRequest)` → `EndpointCheckResponse`
- `deleteEndpointCheck(UUID)` — removes endpoint check config
- `getEndpointChecks(Pageable)` → `Page<EndpointCheckResponse>`
- `runEndpointCheck(UUID)` → `EndpointCheckResponse` — execute check now
- `getLatestResults()` → `List<EndpointCheckResponse>`

### Collector: `MetricCollector` (Interface)

```java
public interface MetricCollector {
    List<MetricSample> collectMetrics(Server server);
    List<ServiceCheck> checkServices(Server server, List<String> serviceNames);
    boolean isReachable(Server server);
}
```

Provider-agnostic abstraction. MVP implementation is `SshMetricCollector`; future implementations could include an agent-based collector.

### Collector: `SshMetricCollector`

Implements `MetricCollector`. Collects all metrics in a single SSH session per server using a batched shell command:

```bash
# Single command that outputs all metrics as key=value pairs
echo "CPU=$(top -bn1 | grep 'Cpu(s)' | awk '{print $2}')"
echo "MEM_TOTAL=$(free -b | awk '/Mem:/{print $2}')"
echo "MEM_USED=$(free -b | awk '/Mem:/{print $3}')"
# ... more metrics
echo "UPTIME=$(cat /proc/uptime | awk '{print $1}')"
echo "LOAD=$(cat /proc/loadavg)"
df -B1 --output=target,size,used,pcent | tail -n+2
```

Parses output into `MetricSample` objects. Target: ~1-2 seconds per server.

### Engine: `HealthEvaluator`

Evaluates collected metrics against thresholds to determine `HealthState`:
- CPU > 90% or Memory > 95% or Disk > 95% → `CRITICAL`
- CPU > 75% or Memory > 85% or Disk > 85% → `WARNING`
- All metrics within normal range → `HEALTHY`
- SSH connection failed → `UNREACHABLE`
- No data yet → `UNKNOWN`
- Active maintenance window → `MAINTENANCE`

Thresholds are configurable per server (future: via `MonitoringConfig` entity).

### Engine: `AlertEngine`

Processes metrics against `AlertRule` definitions:
1. For each new metric batch, check applicable alert rules
2. Track consecutive failures per rule per server
3. When `consecutiveFailures` threshold reached, create `AlertEvent`
4. Respect `cooldownMinutes` — don't re-fire within cooldown period
5. Trigger notification dispatch via `NotificationService`
6. Auto-resolve alerts when condition clears

### Engine: `IncidentManager`

Manages incident lifecycle:
- Auto-create incidents from CRITICAL alerts (configurable)
- Link alert events to incidents
- Track state transitions: OPEN → ACKNOWLEDGED → INVESTIGATING → RESOLVED → CLOSED
- Timeline built from notes + alert events

### Scheduler: `MonitoringScheduler`

Spring `@Scheduled` tasks on dedicated `monitoringExecutor` thread pool:
- `collectAndEvaluate()` — every 60s (configurable): collect metrics → evaluate health → check alerts
- `checkEndpoints()` — every 300s (configurable): run all endpoint checks
- `cleanupOldMetrics()` — daily at 02:00: apply retention policy
- `checkMaintenanceWindows()` — every 60s: start/end maintenance states

### Config: `MonitoringConfig`

Spring `@Configuration` bean:
- `monitoringExecutor` — `ThreadPoolTaskExecutor` for async metric collection
- Default check intervals (configurable via `application.yml`)
- Retention policy settings

### Config: `MonitoringSchedulerConfig`

Scheduling configuration with `@EnableScheduling`, task executor, and cron expressions.

### Repository: `MetricSampleRepository`

- `findByServerIdAndMetricTypeAndCollectedAtBetween(UUID, MetricType, Instant, Instant)` → `List<MetricSample>`
- `findLatestByServerId(UUID serverId)` → `List<MetricSample>` (custom query: latest per metric type)
- `deleteByCollectedAtBefore(Instant)` → `long` (retention cleanup)

### Repository: `HealthSnapshotRepository`

- `findByServerId(UUID)` → `Optional<HealthSnapshot>`
- `findAll()` → `List<HealthSnapshot>`
- `countByState(HealthState)` → `long`

### Repository: `AlertRuleRepository`

- `findByServerIdOrServerIdIsNull(UUID)` → `List<AlertRule>` (server-specific + global rules)
- `findByEnabled(boolean)` → `List<AlertRule>`

### Repository: `AlertEventRepository`

- `findByServerIdOrderByFiredAtDesc(UUID, Pageable)` → `Page<AlertEvent>`
- `findByStatus(AlertEventStatus)` → `List<AlertEvent>`
- `countByStatus(AlertEventStatus)` → `long`

### Repository: `IncidentRepository`

- `findByServerIdOrderByOpenedAtDesc(UUID, Pageable)` → `Page<Incident>`
- `findByStatus(IncidentStatus)` → `List<Incident>`
- `countByStatusIn(List<IncidentStatus>)` → `long`

### Repository: `NotificationChannelRepository`

- `findByEnabled(boolean)` → `List<NotificationChannel>`

### Repository: `MaintenanceWindowRepository`

- `findByServerIdAndStartAtBeforeAndEndAtAfter(UUID, Instant, Instant)` → `List<MaintenanceWindow>`
- `findByEndAtBefore(Instant)` → `List<MaintenanceWindow>` (expired windows)

### Mapper Classes

- `MetricSampleMapper` — entity ↔ response
- `HealthSnapshotMapper` — entity ↔ response (enriches with server name)
- `AlertRuleMapper` — entity ↔ request/response
- `AlertEventMapper` — entity ↔ response (enriches with rule name, server name)
- `IncidentMapper` — entity ↔ request/response (enriches with server name, counts)
- `IncidentNoteMapper` — entity ↔ request/response
- `NotificationChannelMapper` — entity ↔ request/response (**strips config from response**)
- `MaintenanceWindowMapper` — entity ↔ request/response
- `ServiceCheckMapper` — entity ↔ response
- `EndpointCheckMapper` — entity ↔ request/response

## API Endpoints

### Metrics

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/monitoring/metrics` | Yes | Query metrics by server, type, time range |
| GET | `/api/v1/monitoring/metrics/latest/{serverId}` | Yes | Latest metrics for a server |

### Health

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/monitoring/health` | Yes | All server health snapshots |
| GET | `/api/v1/monitoring/health/{serverId}` | Yes | Health snapshot for one server |
| GET | `/api/v1/monitoring/dashboard` | Yes | Aggregated monitoring dashboard |

### Service Checks

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/monitoring/services/{serverId}` | Yes | Latest service checks for a server |
| POST | `/api/v1/monitoring/services/{serverId}/check` | Yes | Trigger service check now |

### Endpoint Checks

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/monitoring/endpoints` | Yes | Create endpoint check |
| GET | `/api/v1/monitoring/endpoints` | Yes | List endpoint checks (paginated) |
| DELETE | `/api/v1/monitoring/endpoints/{id}` | Yes (ADMIN) | Delete endpoint check |
| POST | `/api/v1/monitoring/endpoints/{id}/check` | Yes | Run endpoint check now |
| GET | `/api/v1/monitoring/endpoints/latest` | Yes | Latest results for all endpoints |

### Alert Rules

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/monitoring/alert-rules` | Yes (ADMIN) | Create alert rule |
| GET | `/api/v1/monitoring/alert-rules` | Yes | List alert rules (paginated) |
| GET | `/api/v1/monitoring/alert-rules/{id}` | Yes | Get alert rule |
| PATCH | `/api/v1/monitoring/alert-rules/{id}` | Yes (ADMIN) | Update alert rule |
| DELETE | `/api/v1/monitoring/alert-rules/{id}` | Yes (ADMIN) | Delete alert rule |

### Alert Events

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/monitoring/alerts` | Yes | List alert events (paginated, filterable) |
| GET | `/api/v1/monitoring/alerts/server/{serverId}` | Yes | Alert events for a server |
| POST | `/api/v1/monitoring/alerts/{id}/acknowledge` | Yes | Acknowledge an alert |
| POST | `/api/v1/monitoring/alerts/{id}/resolve` | Yes | Resolve an alert |

### Incidents

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/monitoring/incidents` | Yes | Create incident manually |
| GET | `/api/v1/monitoring/incidents` | Yes | List incidents (paginated) |
| GET | `/api/v1/monitoring/incidents/{id}` | Yes | Get incident detail |
| PATCH | `/api/v1/monitoring/incidents/{id}` | Yes | Update incident status/root cause |
| GET | `/api/v1/monitoring/incidents/server/{serverId}` | Yes | Incidents for a server |
| POST | `/api/v1/monitoring/incidents/{id}/notes` | Yes | Add note to incident |
| GET | `/api/v1/monitoring/incidents/{id}/notes` | Yes | Get incident notes |

### Notification Channels

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/monitoring/notification-channels` | Yes (ADMIN) | Create notification channel |
| GET | `/api/v1/monitoring/notification-channels` | Yes | List channels |
| PATCH | `/api/v1/monitoring/notification-channels/{id}` | Yes (ADMIN) | Update channel |
| DELETE | `/api/v1/monitoring/notification-channels/{id}` | Yes (ADMIN) | Delete channel |
| POST | `/api/v1/monitoring/notification-channels/{id}/test` | Yes (ADMIN) | Send test notification |

### Maintenance Windows

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/monitoring/maintenance-windows` | Yes (ADMIN) | Create maintenance window |
| GET | `/api/v1/monitoring/maintenance-windows` | Yes | List active windows |
| GET | `/api/v1/monitoring/maintenance-windows/server/{serverId}` | Yes | Windows for a server |
| DELETE | `/api/v1/monitoring/maintenance-windows/{id}` | Yes (ADMIN) | Cancel maintenance window |

## Business Rules

- **Metric collection is SSH-based (MVP)** — single batched command per server per check cycle, targeting ~1-2s per server
- **MetricCollector interface** abstracts collection strategy — `SshMetricCollector` is the MVP implementation; future agent-based collectors can be swapped in without rewriting evaluation/alerting logic
- **Health state machine**: `UNKNOWN → HEALTHY ↔ WARNING ↔ CRITICAL`, any state → `UNREACHABLE` on SSH failure, any state → `MAINTENANCE` via manual override, `MAINTENANCE` reverts to previous state when window ends
- **One active HealthSnapshot per server** — upserted on each evaluation cycle, not appended
- **Alert rules can be global** (serverId = null) or per-server — global rules apply to all servers
- **Consecutive failures required** before firing an alert — prevents flapping on transient spikes
- **Alert cooldown** prevents re-firing the same rule within the cooldown window
- **Auto-resolve** — alerts resolve automatically when the triggering condition clears
- **Incidents can be auto-created** from CRITICAL alerts or manually created by users
- **Incident lifecycle**: OPEN → ACKNOWLEDGED → INVESTIGATING → RESOLVED → CLOSED
- **Notification channel config is encrypted** — contains sensitive data (webhook URLs, tokens, API keys)
- **Maintenance windows suppress alerts** — no alerts fire for servers in active maintenance
- **Retention policy**: raw metric samples retained for 7 days, hourly rollups for 90 days, daily rollups for 1 year — cleanup runs daily at 02:00
- **Endpoint checks run independently** from server metric collection — they check external reachability (HTTP, TCP, SSL, DNS)
- **Service checks** detect running systemd services, Docker containers, or named processes on a server

## Security Considerations

- Notification channel config (webhook URLs, tokens, API keys) **must be AES-GCM encrypted** at rest — reuse existing `EncryptionService` from the secrets module
- **Never return notification channel config in API responses** — only return name, type, and enabled status
- Alert rule and notification channel management restricted to **ADMIN role**
- All monitoring configuration changes (rules, channels, maintenance windows) **logged to audit**
- SSH metric collection reuses existing authenticated SSH sessions — no new credential handling needed
- Metric data is internal-only — not exposed to unauthenticated endpoints
- Rate-limit manual check triggers (`/check` endpoints) to prevent SSH abuse

## Dependencies

- **servers** — `Server` entity and `ServerRepository` for server inventory (list of servers to monitor)
- **ssh** — `SshService.executeCommand()` for remote metric collection via SSH
- **secrets** — `EncryptionService` for encrypting notification channel config
- **audit** — `AuditService.log()` for monitoring configuration changes (rules, channels, maintenance)
- **common** — Exceptions (`ResourceNotFoundException`, `ValidationException`), base error handling patterns
- **auth** — Current user context for incident notes, maintenance window creation, alert acknowledgment
