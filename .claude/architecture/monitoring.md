# Monitoring Module — Architecture Log

> This file tracks implemented components for the monitoring module.
> Entries are appended after code is written or significantly modified.

---

### Flyway Migration V17
- **File(s):** `src/main/resources/db/migration/V17__create_monitoring_tables.sql`
- **Type:** migration
- **Description:** Creates all 13 monitoring tables: `monitoring_profiles`, `metric_samples`, `health_snapshots`, `service_checks`, `endpoint_checks`, `endpoint_check_results`, `notification_channels`, `alert_rules`, `alert_rule_channels` (junction), `incidents`, `alert_events`, `incident_events`, `maintenance_windows`. Includes proper indexes on time-series columns, foreign keys with CASCADE/SET NULL, UNIQUE constraints on per-server tables, and BYTEA columns for encrypted notification channel config.
- **Dependencies:** `servers` table (FK), `users` table (FK for incidents/maintenance)
- **Date:** 2026-03-25

### Enums
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/entity/MetricType.java`, `HealthState.java`, `ServiceType.java`, `EndpointCheckType.java`, `AlertRuleType.java`, `AlertStatus.java`, `IncidentStatus.java`, `IncidentSeverity.java`, `NotificationChannelType.java`, `ConditionOperator.java`, `IncidentEventType.java`
- **Type:** enum (11 enums)
- **Description:** All enum types for the monitoring module. `MetricType` (15 metric types), `HealthState` (6 states incl. MAINTENANCE), `ServiceType` (SYSTEMD/DOCKER/PROCESS), `EndpointCheckType` (HTTP/HTTPS/TCP/SSL_CERT/DNS), `AlertRuleType` (THRESHOLD/CONSECUTIVE_FAILURE/DEADMAN), `AlertStatus` (ACTIVE/ACKNOWLEDGED/RESOLVED/SILENCED), `IncidentStatus` (5 states), `IncidentSeverity` (LOW-CRITICAL), `NotificationChannelType` (EMAIL/SLACK/DISCORD/TELEGRAM/WEBHOOK), `ConditionOperator` (6 comparison ops), `IncidentEventType` (NOTE/STATE_CHANGE/ALERT_LINKED/etc.)
- **Dependencies:** None
- **Date:** 2026-03-25

### MonitoringProfile Entity
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/entity/MonitoringProfile.java`
- **Type:** entity
- **Description:** Per-server monitoring configuration. Stores check interval, retention days, and per-resource warning/critical thresholds (CPU, memory, disk) as BigDecimal. One profile per server (UNIQUE on server_id).
- **Dependencies:** servers module (server_id FK)
- **Date:** 2026-03-25

### MetricSample Entity
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/entity/MetricSample.java`
- **Type:** entity
- **Description:** Time-series metric data point. Stores server_id, metric_type (enum), metric_label (for disk mount/network interface), value (double), and collected_at timestamp. High-volume table — indexed on (server_id, metric_type, collected_at DESC).
- **Dependencies:** servers module (server_id FK)
- **Date:** 2026-03-25

### HealthSnapshot Entity
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/entity/HealthSnapshot.java`
- **Type:** entity
- **Description:** Denormalized current health state per server. One row per server (UNIQUE), upserted on each check cycle. Stores overall_state + per-resource states (cpu/memory/disk as HealthState enums), latest metric values, consecutive_failures count, ssh_reachable flag, and JSON details field.
- **Dependencies:** servers module (server_id FK)
- **Date:** 2026-03-25

### ServiceCheck Entity
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/entity/ServiceCheck.java`
- **Type:** entity
- **Description:** Result of checking a service on a server. Tracks service_name, service_type (SYSTEMD/DOCKER/PROCESS), is_running, pid, memory/cpu usage, and checked_at.
- **Dependencies:** servers module (server_id FK)
- **Date:** 2026-03-25

### EndpointCheck Entity
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/entity/EndpointCheck.java`
- **Type:** entity
- **Description:** Endpoint check definition (what to check). Stores name, url, check_type, expected_status_code, enabled flag, and interval_seconds. Optionally linked to a server. Results stored separately in EndpointCheckResult.
- **Dependencies:** servers module (optional server_id FK)
- **Date:** 2026-03-25

### EndpointCheckResult Entity
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/entity/EndpointCheckResult.java`
- **Type:** entity
- **Description:** Result history for endpoint checks. Stores is_up, response_time_ms, status_code, SSL expiry info, and error_message. Linked to EndpointCheck via endpoint_check_id.
- **Dependencies:** EndpointCheck entity
- **Date:** 2026-03-25

### NotificationChannel Entity
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/entity/NotificationChannel.java`
- **Type:** entity
- **Description:** Notification channel configuration (email, Slack, Discord, Telegram, webhook). Config stored as AES-GCM encrypted BYTEA (config + config_iv columns) — never exposed in API responses.
- **Dependencies:** secrets module pattern (encryption)
- **Date:** 2026-03-25

### AlertRule Entity
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/entity/AlertRule.java`
- **Type:** entity
- **Description:** Alert rule definition. Supports THRESHOLD, CONSECUTIVE_FAILURE, and DEADMAN rule types. Configurable metric_type, condition_operator, threshold_value, severity, consecutive_failures count, and cooldown_minutes. Optional server_id (null = global rule). Uses @ManyToMany with NotificationChannel via alert_rule_channels junction table.
- **Dependencies:** NotificationChannel entity, servers module (optional server_id FK)
- **Date:** 2026-03-25

### AlertEvent Entity
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/entity/AlertEvent.java`
- **Type:** entity
- **Description:** Fired alert instance. Links to alert_rule_id, server_id, optional incident_id. Tracks severity, status (ACTIVE/ACKNOWLEDGED/RESOLVED/SILENCED), metric_type/value that triggered it, message, acknowledgment info, and fired_at timestamp.
- **Dependencies:** AlertRule, Incident, servers module, users module
- **Date:** 2026-03-25

### Incident Entity
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/entity/Incident.java`
- **Type:** entity
- **Description:** Incident lifecycle tracking. Stores title, description, server_id, severity, status (OPEN→ACKNOWLEDGED→INVESTIGATING→RESOLVED→CLOSED), timestamps for each state transition, resolved_by user, and root_cause.
- **Dependencies:** servers module (server_id FK), users module (resolved_by FK)
- **Date:** 2026-03-25

### IncidentEvent Entity
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/entity/IncidentEvent.java`
- **Type:** entity
- **Description:** Incident timeline entries. Supports multiple event types (NOTE, STATE_CHANGE, ALERT_LINKED, etc.) via IncidentEventType enum. Stores author_id, content, and created_at. Used to build incident timeline.
- **Dependencies:** Incident entity, users module (author_id FK)
- **Date:** 2026-03-25

### MaintenanceWindow Entity
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/entity/MaintenanceWindow.java`
- **Type:** entity
- **Description:** Scheduled maintenance period for a server. During active windows, the server's health state is overridden to MAINTENANCE and alerts are suppressed. Stores server_id, reason, start_at, end_at, created_by.
- **Dependencies:** servers module (server_id FK), users module (created_by FK)
- **Date:** 2026-03-25

### Repositories (12)
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/repository/*.java`
- **Type:** repository
- **Description:** One repository per entity: MonitoringProfileRepository, MetricSampleRepository (with custom @Query for latest-per-type and retention cleanup), HealthSnapshotRepository (with countByOverallState), ServiceCheckRepository, EndpointCheckRepository, EndpointCheckResultRepository (with retention cleanup), NotificationChannelRepository, AlertRuleRepository (with findByServerIdOrServerIdIsNull for global+specific rules), AlertEventRepository (paginated, with status filters), IncidentRepository (paginated, with countByStatusIn), IncidentEventRepository, MaintenanceWindowRepository (with active window queries). All extend JpaRepository<Entity, UUID>.
- **Dependencies:** All monitoring entities
- **Date:** 2026-03-25

### CollectionResult Record
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/collector/CollectionResult.java`
- **Type:** record (DTO-like)
- **Description:** Immutable result of a metric collection cycle. Contains serverId, Map<String, Double> metrics (keyed by MetricType name, with optional ":label" suffix for per-mount/per-interface metrics), collectedAt timestamp, durationMs, sshReachable flag, and list of partial-failure error messages.
- **Dependencies:** None
- **Date:** 2026-03-25

### MetricCollector Interface
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/collector/MetricCollector.java`
- **Type:** interface
- **Description:** Provider-agnostic abstraction for metric collection. Defines `collect(Server)` returning `CollectionResult` and `getCollectorType()` returning a string identifier. Allows future agent-based implementations to replace SSH without changing evaluation/alerting logic.
- **Dependencies:** servers module (Server entity)
- **Date:** 2026-03-25

### SshMetricCollector
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/collector/SshMetricCollector.java`
- **Type:** component (implements MetricCollector)
- **Description:** SSH-based metric collector. Sends a single batched shell command with section delimiters (---CPU---, ---MEM---, etc.) and parses each section independently for graceful partial failure. Collects: CPU usage (from top idle), memory (from free -b), disk per-mount (from df -B1 with fallback to df -k), load averages (from /proc/loadavg), uptime (from /proc/uptime), process count (ps aux | wc -l), swap (from free), and per-interface network RX/TX bytes (from /proc/net/dev). 30-second SSH timeout. Cross-distro compatible (Ubuntu, Debian, CentOS, RHEL, Amazon Linux). No sudo required.
- **Dependencies:** ssh module (SshService.executeCommand), MetricCollector interface
- **Date:** 2026-03-25

### MetricsService
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/service/MetricsService.java`
- **Type:** service
- **Description:** (Refactored in task-27) Core service that processes CollectionResult from collectors. Persists MetricSample records (bulk insert) and delegates health evaluation to HealthEvaluator. Provides queryMetrics, getLatestMetrics, and deleteOldMetrics for API and retention cleanup. Health evaluation logic was extracted to HealthEvaluator for separation of concerns.
- **Dependencies:** MetricSampleRepository, HealthEvaluator
- **Date:** 2026-03-25

### Flyway Migration V18
- **File(s):** `src/main/resources/db/migration/V18__add_health_snapshot_flapping_columns.sql`
- **Type:** migration
- **Description:** Adds flapping protection columns to health_snapshots: `proposed_state` (VARCHAR), `consecutive_in_proposed` (INT), `previous_state` (VARCHAR), `state_changed_at` (TIMESTAMPTZ). These support the state machine's requirement to track N consecutive evaluations before transitioning state.
- **Dependencies:** V17 health_snapshots table
- **Date:** 2026-03-25

### HealthSnapshot Entity (modified)
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/entity/HealthSnapshot.java`
- **Type:** entity (modified)
- **Description:** Added 4 fields for flapping protection: `proposedState` (the state being considered), `consecutiveInProposed` (how many consecutive checks have computed this proposed state), `previousState` (state before last transition), `stateChangedAt` (timestamp of last state transition). These fields are used by HealthEvaluator to prevent premature state changes on transient spikes.
- **Dependencies:** None
- **Date:** 2026-03-25

### HealthEvaluator
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/engine/HealthEvaluator.java`
- **Type:** service (engine)
- **Description:** Dedicated health evaluation engine with state machine and flapping protection. Takes CollectionResult, evaluates per-resource health states (CPU/memory/disk) against configurable thresholds from MonitoringProfile (defaults: CPU 80/95%, memory 80/95%, disk 85/95%). Overall state = pessimistic aggregation (worst of all resources). State transition rules with flapping protection: HEALTHY→WARNING requires 2 consecutive, WARNING→HEALTHY requires 2, CRITICAL→HEALTHY requires 3, CRITICAL→WARNING requires 2. Immediate transitions for: UNKNOWN→any, any→CRITICAL, any→UNREACHABLE, any→MAINTENANCE. Logs state changes as IncidentEvent (STATE_CHANGE type) for timeline history. Respects maintenance windows.
- **Dependencies:** HealthSnapshotRepository, MonitoringProfileRepository, MaintenanceWindowRepository, IncidentEventRepository
- **Date:** 2026-03-25

### MonitoringSchedulerConfig
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/config/MonitoringSchedulerConfig.java`
- **Type:** config
- **Description:** Spring @Configuration with @EnableScheduling. Creates `monitoringExecutor` ThreadPoolTaskExecutor bean (core=4, max=20, queue=100, prefix="monitor-"). All pool sizes configurable via properties. Graceful shutdown: waits up to 30s for in-flight tasks to complete.
- **Dependencies:** None (Spring infrastructure)
- **Date:** 2026-03-25

### MonitoringScheduler
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/scheduler/MonitoringScheduler.java`
- **Type:** service (scheduler)
- **Description:** Core scheduling service that orchestrates periodic metric collection. Runs on @Scheduled(fixedDelay=30s configurable). Each tick: queries all servers, filters to those with enabled profiles due for check, sorts by priority (CRITICAL first, HEALTHY last), submits async check tasks to monitoringExecutor. Each task: collect → process (persist + evaluate health). Concurrency control via ConcurrentHashMap — max 1 active check per server. Auto-creates MonitoringProfile with defaults for servers without one (opt-out model). Stale detection: marks servers UNKNOWN if last_check_at > 3× interval. Startup: @PostConstruct schedules immediate check for all servers. Retention cleanup: @Scheduled(cron 3AM daily) deletes metric_samples older than retention period. Logs tick duration, submitted/skipped counts.
- **Dependencies:** ServerRepository, MonitoringProfileRepository, HealthSnapshotRepository, MetricCollector, MetricsService, monitoringExecutor
- **Date:** 2026-03-25

### Monitoring Config Properties
- **File(s):** `src/main/resources/application.properties` (monitoring section added)
- **Type:** config
- **Description:** Added monitoring configuration properties: scheduler.interval (30s), check.default-interval (60s), check.timeout (30s), check.stale-multiplier (3), metrics.retention-days (7), executor pool sizes (4/20/100). All overridable via environment variables (MONITORING_*).
- **Dependencies:** None
- **Date:** 2026-03-25

### Monitoring DTOs
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/dto/*.java`
- **Type:** dto (8 records)
- **Description:** FleetHealthResponse (fleet overview with counts + server list), ServerHealthSummary (per-server health with all metric values), MetricHistoryResponse + MetricPoint (time-series chart data), MonitoringProfileResponse, UpdateMonitoringProfileRequest (partial update with Jakarta validation), CreateMaintenanceWindowRequest, MaintenanceWindowResponse. All use Java record syntax.
- **Dependencies:** monitoring entities/enums
- **Date:** 2026-03-25

### Monitoring Mappers
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/mapper/HealthSnapshotMapper.java`, `MonitoringProfileMapper.java`, `MaintenanceWindowMapper.java`
- **Type:** mapper (3 classes)
- **Description:** Static utility classes with private constructors. HealthSnapshotMapper: toSummary(snapshot, server) and toSummaryNoSnapshot(server) for UNKNOWN state. MonitoringProfileMapper: toResponse(profile). MaintenanceWindowMapper: toResponse(window, serverName).
- **Dependencies:** DTOs, entities, Server entity
- **Date:** 2026-03-25

### HealthService
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/service/HealthService.java`
- **Type:** service
- **Description:** Provides fleet health overview and individual server health detail. getFleetHealth() joins all servers with their health snapshots, filters by environment/state, counts by state, sorts worst-first. getServerHealth() returns single server summary. Servers without snapshots show as UNKNOWN.
- **Dependencies:** HealthSnapshotRepository, ServerRepository, HealthSnapshotMapper
- **Date:** 2026-03-25

### MonitoringProfileService
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/service/MonitoringProfileService.java`
- **Type:** service
- **Description:** CRUD for per-server monitoring profiles. getProfile() auto-creates with defaults if none exists. updateProfile() does partial update (null fields not changed). resetProfile() deletes existing and creates fresh defaults. Validates server exists before all operations.
- **Dependencies:** MonitoringProfileRepository, ServerRepository
- **Date:** 2026-03-25

### MaintenanceService
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/service/MaintenanceService.java`
- **Type:** service
- **Description:** Manages maintenance windows. createWindow() validates server exists and persists. getActiveWindows() returns currently active windows. getAllWindows() returns all. deleteWindow() cancels a window. Enriches responses with server names via batch lookup.
- **Dependencies:** MaintenanceWindowRepository, ServerRepository
- **Date:** 2026-03-25

### MonitoringController
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/monitoring/controller/MonitoringController.java`
- **Type:** controller
- **Description:** REST controller at /api/v1/monitoring with all MVP endpoints. Health: GET /health (fleet overview, filterable by environment/state), GET /health/{serverId}. Metrics: GET /metrics/{serverId} (time-range history), GET /metrics/{serverId}/latest, POST /check/{serverId} (async trigger). Profiles: GET/PATCH /profiles/{serverId}, POST /profiles/{serverId}/reset. Maintenance: GET/POST /maintenance, DELETE /maintenance/{id}. All secured with bearerAuth. Swagger-annotated.
- **Dependencies:** HealthService, MetricsService, MonitoringProfileService, MaintenanceService, MetricCollector, ServerRepository, monitoringExecutor
- **Date:** 2026-03-25

### Monitoring Dev Admin Page
- **File(s):** `src/main/resources/static/dev/monitoring.html`, `src/main/resources/static/dev/index.html` (modified)
- **Type:** frontend (static HTML/CSS/JS)
- **Description:** Full monitoring dashboard at /dev/monitoring.html. Fleet overview with summary cards (healthy/warning/critical/unreachable/unknown/maintenance counts), filterable server health table with color-coded badges and metric bars, expandable detail panels with all metrics + canvas line charts (CPU/memory/disk over 1h/6h/24h/7d), monitoring profile editor modal (thresholds, interval, enable/disable), maintenance window management with create/cancel. Auto-refreshes every 30s. Added "Monitoring" card to dashboard index.html.
- **Dependencies:** common.js, common.css, monitoring REST API
- **Date:** 2026-03-25
