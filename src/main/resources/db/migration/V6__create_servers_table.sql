CREATE TABLE servers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    hostname VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    ssh_port INTEGER NOT NULL DEFAULT 22,
    ssh_username VARCHAR(100) NOT NULL,
    auth_type VARCHAR(20) NOT NULL,
    credential_id UUID REFERENCES secrets(id) ON DELETE SET NULL,
    environment VARCHAR(50) NOT NULL,
    root_domain VARCHAR(255),
    subdomain VARCHAR(255),
    ssl_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_servers_name ON servers(name);
CREATE INDEX idx_servers_environment ON servers(environment);
CREATE INDEX idx_servers_status ON servers(status);
CREATE INDEX idx_servers_credential_id ON servers(credential_id);
