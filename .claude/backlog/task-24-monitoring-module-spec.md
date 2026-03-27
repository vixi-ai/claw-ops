# Task 24: Monitoring Module — Module Spec & Architecture Doc

**Status:** DONE
**Module(s):** monitoring, common
**Priority:** HIGH
**Created:** 2026-03-25
**Completed:** 2026-03-25

## Description

Create the module specification (`.claude/modules/monitoring.md`) and architecture document (`.claude/architecture/monitoring.md`) for the new Monitoring & Health Check module. This is the foundational planning document that all subsequent monitoring tasks depend on.

The monitoring module transforms ClawOps from a server management tool into a full infrastructure observability platform. It adds continuous health monitoring, alerting, incident tracking, and historical metrics — all built on the existing SSH infrastructure.

### Product Goals

1. **Real-time server health visibility** — know the state of every managed server at a glance
2. **Proactive alerting** — detect problems before users report them
3. **Historical metrics** — track trends, capacity, and performance over time
4. **Service monitoring** — track systemd services, Docker containers, nginx, databases
5. **Endpoint monitoring** — verify HTTP/HTTPS endpoints, SSL certs, DNS, ports
6. **Incident management** — lightweight incident lifecycle with timeline and notes
7. **Non-intrusive** — SSH-based, no agents to install, works with existing server inventory

### Architecture Decisions

- **SSH-based collection (MVP)** with `MetricCollector` interface for future agent support
- **Batched SSH commands** — single connection per server per check cycle (~1-2s per server)
- **PostgreSQL storage** — metrics, alerts, incidents all in same DB with retention cleanup
- **Async scheduled execution** — `monitoringExecutor` ThreadPoolTaskExecutor + Spring `@Scheduled`
- **Health state machine** — HEALTHY/WARNING/CRITICAL/UNREACHABLE/UNKNOWN/MAINTENANCE
- **Provider-agnostic design** — abstractions allow SSH→Agent migration without rewrite

## Acceptance Criteria

- [x] `.claude/modules/monitoring.md` created with full module spec following existing format
- [x] Module spec includes: purpose, package, components list, entity definitions, DTO definitions, API endpoints table, business rules, security considerations, dependencies
- [x] `.claude/architecture/monitoring.md` created (empty initially, populated as components are built)
- [x] Module spec references the MetricCollector interface abstraction for future agent support
- [x] Health state model documented: HEALTHY, WARNING, CRITICAL, UNREACHABLE, UNKNOWN, MAINTENANCE
- [x] SSH command strategy documented (batched single-command approach)
- [x] Check types documented: infrastructure metrics, service checks, endpoint checks
- [x] Alert model documented: rules → events → incidents lifecycle
- [x] Notification channel model documented: email, Slack, webhook, Discord, Telegram
- [x] Retention policy documented: raw metrics (7 days), hourly rollups (90 days), daily rollups (1 year)

## Implementation Notes

### Module Package Structure
```
com.openclaw.manager.openclawserversmanager.monitoring/
  config/          — MonitoringConfig, MonitoringSchedulerConfig
  entity/          — MetricSample, HealthSnapshot, ServiceCheck, EndpointCheck, AlertRule, AlertEvent, Incident, etc.
  dto/             — Request/Response DTOs for all entities
  repository/      — JPA repositories
  service/         — Core services (MetricsService, HealthService, AlertService, etc.)
  collector/       — MetricCollector interface + SshMetricCollector implementation
  engine/          — HealthEvaluator, AlertEngine, IncidentManager
  scheduler/       — MonitoringScheduler (Spring @Scheduled)
  controller/      — REST controllers
  mapper/          — Entity↔DTO mappers
```

### Health State Model
```
UNKNOWN → HEALTHY → WARNING → CRITICAL
                 ↘ UNREACHABLE
Any state → MAINTENANCE (manual override)
MAINTENANCE → previous state (when window ends)
```

### Metric Types (MVP)
- CPU_USAGE_PERCENT
- MEMORY_USAGE_PERCENT, MEMORY_USED_BYTES, MEMORY_TOTAL_BYTES
- DISK_USAGE_PERCENT, DISK_USED_BYTES, DISK_TOTAL_BYTES (per mount)
- LOAD_1M, LOAD_5M, LOAD_15M
- UPTIME_SECONDS
- PROCESS_COUNT
- SWAP_USAGE_PERCENT
- NETWORK_RX_BYTES, NETWORK_TX_BYTES (per interface)

### Dependencies on Existing Modules
- **servers** — Server entity, ServerRepository (list of servers to monitor)
- **ssh** — SshService.executeCommand() for remote metric collection
- **audit** — AuditService.log() for monitoring configuration changes
- **common** — Exceptions, error handling patterns

## Files Modified
- `.claude/modules/monitoring.md` — Full module specification (entities, DTOs, services, endpoints, business rules)
- `.claude/architecture/monitoring.md` — Architecture log placeholder (empty, populated as components are built)
