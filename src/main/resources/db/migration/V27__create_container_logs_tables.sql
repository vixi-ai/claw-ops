-- Container logs feature: per-service retention config + captured stdout/stderr lines.

CREATE TABLE container_log_retention_settings (
    service             VARCHAR(20) PRIMARY KEY,
    retention_days      INTEGER     NOT NULL CHECK (retention_days BETWEEN 1 AND 3650),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by_user_id  UUID
);

INSERT INTO container_log_retention_settings (service, retention_days) VALUES
    ('BACKEND',  7),
    ('FRONTEND', 7),
    ('NGINX',    7),
    ('POSTGRES', 7);

CREATE TABLE container_logs (
    id              BIGSERIAL    PRIMARY KEY,
    service         VARCHAR(20)  NOT NULL,
    container_id    VARCHAR(64)  NOT NULL,
    container_name  VARCHAR(128) NOT NULL,
    stream          VARCHAR(8)   NOT NULL,
    level           VARCHAR(8)   NOT NULL,
    message         TEXT         NOT NULL,
    log_ts          TIMESTAMPTZ  NOT NULL,
    ingested_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_container_logs_service_log_ts        ON container_logs (service, log_ts DESC);
CREATE INDEX idx_container_logs_log_ts                ON container_logs (log_ts);
CREATE INDEX idx_container_logs_service_level_log_ts  ON container_logs (service, level, log_ts DESC);

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_container_logs_message_trgm ON container_logs USING gin (message gin_trgm_ops);
