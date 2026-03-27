# Task 25: Monitoring Data Model & Flyway Migrations

**Status:** DONE
**Module(s):** monitoring
**Priority:** HIGH
**Created:** 2026-03-25
**Completed:** 2026-03-25

## Description

Create all database tables for the monitoring module via Flyway migrations, and the corresponding JPA entities, enums, and repositories. This is the data foundation for all monitoring features.

## Acceptance Criteria

### Flyway Migration V17__create_monitoring_tables.sql
- [x] `monitoring_profiles` — per-server monitoring configuration
- [x] `metric_samples` — time-series metric data (the core metrics table)
- [x] `health_snapshots` — current health state per server (denormalized for fast reads)
- [x] `service_checks` — tracked services per server (systemd, Docker, custom)
- [x] `endpoint_checks` — HTTP/TCP/SSL endpoint definitions
- [x] `endpoint_check_results` — endpoint check history
- [x] `alert_rules` — threshold/deadman/consecutive-failure rules
- [x] `alert_events` — triggered/resolved/acknowledged alerts
- [x] `incidents` — incident lifecycle tracking
- [x] `incident_events` — incident timeline entries
- [x] `notification_channels` — email/Slack/webhook/Discord channel configs
- [x] `maintenance_windows` — scheduled maintenance periods
- [x] All tables have proper indexes, FKs, and constraints

### JPA Entities (one per table)
- [x] All entities follow existing patterns: UUID PK, Instant timestamps, @Enumerated(STRING)
- [x] All entities have getters/setters (no Lombok — match existing style)
- [x] Enums created: MetricType, HealthState, EndpointCheckType, ServiceType, AlertRuleType, AlertStatus, IncidentStatus, IncidentSeverity, NotificationChannelType, ConditionOperator, IncidentEventType

### Repositories
- [x] One repository per entity with standard Spring Data methods
- [x] Custom query methods for: metrics by server+type+timerange, active alerts, health snapshots

## Implementation Notes

### Key Tables

#### monitoring_profiles
```sql
CREATE TABLE monitoring_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT true,
    check_interval_seconds INT NOT NULL DEFAULT 60,
    metric_retention_days INT NOT NULL DEFAULT 7,
    cpu_warning_threshold NUMERIC(5,2) DEFAULT 80.0,
    cpu_critical_threshold NUMERIC(5,2) DEFAULT 95.0,
    memory_warning_threshold NUMERIC(5,2) DEFAULT 80.0,
    memory_critical_threshold NUMERIC(5,2) DEFAULT 95.0,
    disk_warning_threshold NUMERIC(5,2) DEFAULT 85.0,
    disk_critical_threshold NUMERIC(5,2) DEFAULT 95.0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(server_id)
);
```

#### metric_samples
```sql
CREATE TABLE metric_samples (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    metric_type VARCHAR(50) NOT NULL,
    metric_label VARCHAR(100),  -- e.g., mount point for disk, interface for network
    value DOUBLE PRECISION NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_metric_samples_server_type_time ON metric_samples(server_id, metric_type, collected_at DESC);
CREATE INDEX idx_metric_samples_collected_at ON metric_samples(collected_at);
```

#### health_snapshots
```sql
CREATE TABLE health_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    overall_state VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    cpu_state VARCHAR(20) DEFAULT 'UNKNOWN',
    memory_state VARCHAR(20) DEFAULT 'UNKNOWN',
    disk_state VARCHAR(20) DEFAULT 'UNKNOWN',
    ssh_reachable BOOLEAN DEFAULT false,
    last_check_at TIMESTAMPTZ,
    last_successful_check_at TIMESTAMPTZ,
    consecutive_failures INT DEFAULT 0,
    cpu_usage DOUBLE PRECISION,
    memory_usage DOUBLE PRECISION,
    disk_usage DOUBLE PRECISION,
    load_1m DOUBLE PRECISION,
    uptime_seconds BIGINT,
    process_count INT,
    details TEXT,  -- JSON with full metric breakdown
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(server_id)
);
```

### Enum Definitions

```java
public enum MetricType {
    CPU_USAGE_PERCENT, MEMORY_USAGE_PERCENT, MEMORY_USED_BYTES, MEMORY_TOTAL_BYTES,
    DISK_USAGE_PERCENT, DISK_USED_BYTES, DISK_TOTAL_BYTES,
    LOAD_1M, LOAD_5M, LOAD_15M,
    UPTIME_SECONDS, PROCESS_COUNT, SWAP_USAGE_PERCENT,
    NETWORK_RX_BYTES, NETWORK_TX_BYTES
}

public enum HealthState {
    HEALTHY, WARNING, CRITICAL, UNREACHABLE, UNKNOWN, MAINTENANCE
}

public enum AlertRuleType {
    THRESHOLD, CONSECUTIVE_FAILURE, DEADMAN
}

public enum AlertStatus {
    ACTIVE, ACKNOWLEDGED, RESOLVED, SILENCED
}

public enum IncidentStatus {
    OPEN, ACKNOWLEDGED, INVESTIGATING, RESOLVED, CLOSED
}

public enum IncidentSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

public enum NotificationChannelType {
    EMAIL, SLACK, DISCORD, TELEGRAM, WEBHOOK
}
```

### Retention Strategy
- Raw metric_samples: 7 days default (configurable per profile)
- Scheduled cleanup job: `@Scheduled(cron = "0 0 3 * * *")` — runs daily at 3 AM
- Future: hourly rollup table for long-term trends (Phase 2)

## Files Modified
- `src/main/resources/db/migration/V17__create_monitoring_tables.sql` — 13 tables with indexes, FKs, constraints
- `src/main/java/.../monitoring/entity/` — 12 entity classes + 11 enum classes
- `src/main/java/.../monitoring/repository/` — 12 repository interfaces
- `.claude/architecture/monitoring.md` — Architecture log updated with all components
