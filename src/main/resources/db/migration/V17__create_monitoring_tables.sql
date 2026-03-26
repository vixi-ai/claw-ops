-- ============================================================
-- V17: Monitoring Module Tables
-- ============================================================

-- Per-server monitoring configuration
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
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(server_id)
);

-- Time-series metric data
CREATE TABLE metric_samples (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    metric_type VARCHAR(50) NOT NULL,
    metric_label VARCHAR(100),
    value DOUBLE PRECISION NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_metric_samples_server_type_time ON metric_samples(server_id, metric_type, collected_at DESC);
CREATE INDEX idx_metric_samples_collected_at ON metric_samples(collected_at);

-- Current health state per server (one row per server, upserted)
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
    details TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(server_id)
);

-- Tracked services per server
CREATE TABLE service_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    service_name VARCHAR(200) NOT NULL,
    service_type VARCHAR(20) NOT NULL,
    is_running BOOLEAN NOT NULL DEFAULT false,
    pid INT,
    memory_usage_bytes BIGINT,
    cpu_usage_percent DOUBLE PRECISION,
    details TEXT,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_service_checks_server ON service_checks(server_id, checked_at DESC);

-- Endpoint check definitions
CREATE TABLE endpoint_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id UUID REFERENCES servers(id) ON DELETE SET NULL,
    name VARCHAR(100) NOT NULL,
    url VARCHAR(2000) NOT NULL,
    check_type VARCHAR(20) NOT NULL,
    expected_status_code INT DEFAULT 200,
    enabled BOOLEAN NOT NULL DEFAULT true,
    interval_seconds INT NOT NULL DEFAULT 300,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Endpoint check result history
CREATE TABLE endpoint_check_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_check_id UUID NOT NULL REFERENCES endpoint_checks(id) ON DELETE CASCADE,
    is_up BOOLEAN NOT NULL,
    response_time_ms BIGINT,
    status_code INT,
    ssl_expires_at TIMESTAMPTZ,
    ssl_days_remaining INT,
    error_message TEXT,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_endpoint_check_results_check_time ON endpoint_check_results(endpoint_check_id, checked_at DESC);
CREATE INDEX idx_endpoint_check_results_checked_at ON endpoint_check_results(checked_at);

-- Notification channels (email, Slack, Discord, Telegram, webhook)
CREATE TABLE notification_channels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    channel_type VARCHAR(20) NOT NULL,
    config BYTEA NOT NULL,
    config_iv BYTEA NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Alert rules (threshold definitions)
CREATE TABLE alert_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    server_id UUID REFERENCES servers(id) ON DELETE CASCADE,
    rule_type VARCHAR(30) NOT NULL,
    metric_type VARCHAR(50),
    condition_operator VARCHAR(30) NOT NULL,
    threshold_value DOUBLE PRECISION NOT NULL,
    severity VARCHAR(20) NOT NULL,
    consecutive_failures INT NOT NULL DEFAULT 3,
    cooldown_minutes INT NOT NULL DEFAULT 15,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_alert_rules_server ON alert_rules(server_id);
CREATE INDEX idx_alert_rules_enabled ON alert_rules(enabled);

-- Junction table: alert rules → notification channels
CREATE TABLE alert_rule_channels (
    alert_rule_id UUID NOT NULL REFERENCES alert_rules(id) ON DELETE CASCADE,
    notification_channel_id UUID NOT NULL REFERENCES notification_channels(id) ON DELETE CASCADE,
    PRIMARY KEY (alert_rule_id, notification_channel_id)
);

-- Incidents
CREATE TABLE incidents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    server_id UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acknowledged_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    root_cause TEXT
);

CREATE INDEX idx_incidents_server ON incidents(server_id);
CREATE INDEX idx_incidents_status ON incidents(status);

-- Alert events (triggered alerts)
CREATE TABLE alert_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_rule_id UUID NOT NULL REFERENCES alert_rules(id) ON DELETE CASCADE,
    server_id UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    incident_id UUID REFERENCES incidents(id) ON DELETE SET NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    metric_type VARCHAR(50),
    metric_value DOUBLE PRECISION,
    message TEXT NOT NULL,
    acknowledged_by UUID REFERENCES users(id) ON DELETE SET NULL,
    acknowledged_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    fired_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_alert_events_server ON alert_events(server_id, fired_at DESC);
CREATE INDEX idx_alert_events_status ON alert_events(status);
CREATE INDEX idx_alert_events_incident ON alert_events(incident_id);

-- Incident timeline events (notes, state changes, linked alerts)
CREATE TABLE incident_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    author_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_incident_events_incident ON incident_events(incident_id, created_at);

-- Maintenance windows
CREATE TABLE maintenance_windows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    reason VARCHAR(500) NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_maintenance_windows_server ON maintenance_windows(server_id, start_at, end_at);
