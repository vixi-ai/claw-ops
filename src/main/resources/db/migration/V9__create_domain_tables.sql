-- ============================================================
-- V9: Domain Management Tables
-- Provider accounts, managed zones, domain assignments, events
-- ============================================================

-- Provider accounts: credentials + settings for one DNS provider account
CREATE TABLE provider_accounts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_type     VARCHAR(30)  NOT NULL,
    display_name      VARCHAR(100) NOT NULL UNIQUE,
    enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    credential_id     UUID         NOT NULL REFERENCES secrets(id) ON DELETE RESTRICT,
    provider_settings TEXT,
    health_status     VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_provider_accounts_type ON provider_accounts(provider_type);
CREATE INDEX idx_provider_accounts_credential ON provider_accounts(credential_id);

-- Managed zones: one domain/zone attached to one provider account
CREATE TABLE managed_zones (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_name           VARCHAR(255) NOT NULL,
    provider_account_id UUID         NOT NULL REFERENCES provider_accounts(id) ON DELETE RESTRICT,
    active              BOOLEAN      NOT NULL DEFAULT FALSE,
    default_ttl         INTEGER      NOT NULL DEFAULT 300,
    provider_zone_id    VARCHAR(255),
    environment_tag     VARCHAR(50),
    metadata            TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(zone_name, provider_account_id)
);

CREATE INDEX idx_managed_zones_provider ON managed_zones(provider_account_id);
CREATE INDEX idx_managed_zones_name ON managed_zones(zone_name);

-- Domain assignments: hostname records created for servers/agents/custom resources
CREATE TABLE domain_assignments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_id             UUID         NOT NULL REFERENCES managed_zones(id) ON DELETE RESTRICT,
    hostname            VARCHAR(255) NOT NULL,
    record_type         VARCHAR(10)  NOT NULL DEFAULT 'A',
    target_value        VARCHAR(255) NOT NULL,
    assignment_type     VARCHAR(20)  NOT NULL,
    resource_id         UUID,
    status              VARCHAR(30)  NOT NULL DEFAULT 'REQUESTED',
    provider_record_id  VARCHAR(255),
    desired_state_hash  VARCHAR(64),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_domain_assignments_hostname ON domain_assignments(hostname) WHERE status != 'RELEASED';
CREATE INDEX idx_domain_assignments_zone ON domain_assignments(zone_id);
CREATE INDEX idx_domain_assignments_resource ON domain_assignments(resource_id);
CREATE INDEX idx_domain_assignments_status ON domain_assignments(status);

-- Domain events: granular operational log with provider correlation IDs
CREATE TABLE domain_events (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id           UUID REFERENCES domain_assignments(id) ON DELETE CASCADE,
    zone_id                 UUID REFERENCES managed_zones(id) ON DELETE CASCADE,
    action                  VARCHAR(50)  NOT NULL,
    outcome                 VARCHAR(20)  NOT NULL,
    provider_correlation_id VARCHAR(255),
    details                 TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_domain_events_assignment ON domain_events(assignment_id);
CREATE INDEX idx_domain_events_zone ON domain_events(zone_id);
CREATE INDEX idx_domain_events_created ON domain_events(created_at DESC);
