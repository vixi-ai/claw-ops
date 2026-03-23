CREATE TABLE deployment_scripts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    script_content TEXT NOT NULL,
    script_type VARCHAR(30) NOT NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE deployment_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    script_id UUID REFERENCES deployment_scripts(id) ON DELETE SET NULL,
    script_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    triggered_by UUID REFERENCES users(id) ON DELETE SET NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    logs TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_deployment_jobs_server_id ON deployment_jobs(server_id);
CREATE INDEX idx_deployment_jobs_status ON deployment_jobs(status);
