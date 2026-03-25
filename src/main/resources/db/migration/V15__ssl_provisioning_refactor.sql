-- Provisioning jobs: async SSL provisioning with state machine
CREATE TABLE provisioning_jobs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    domain_assignment_id    UUID NOT NULL REFERENCES domain_assignments(id) ON DELETE CASCADE,
    server_id               UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    current_step            VARCHAR(30) NOT NULL DEFAULT 'PENDING_DNS',
    status                  VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    retry_count             INTEGER NOT NULL DEFAULT 0,
    max_retries             INTEGER NOT NULL DEFAULT 3,
    logs                    TEXT,
    error_message           TEXT,
    acme_txt_record_id      VARCHAR(255),
    triggered_by            UUID,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    started_at              TIMESTAMP WITH TIME ZONE,
    finished_at             TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_prov_jobs_assignment ON provisioning_jobs(domain_assignment_id);
CREATE INDEX idx_prov_jobs_server ON provisioning_jobs(server_id);
CREATE INDEX idx_prov_jobs_status ON provisioning_jobs(status);

-- Make server_id nullable on ssl_certificates (cert is now per-assignment, not per-server)
ALTER TABLE ssl_certificates ALTER COLUMN server_id DROP NOT NULL;

-- Add link from cert to the job that provisioned it
ALTER TABLE ssl_certificates ADD COLUMN IF NOT EXISTS provisioning_job_id UUID REFERENCES provisioning_jobs(id) ON DELETE SET NULL;

-- One active cert per domain assignment (prevents duplicates)
CREATE UNIQUE INDEX idx_ssl_certs_assignment_active
    ON ssl_certificates(assignment_id)
    WHERE status NOT IN ('FAILED', 'EXPIRED', 'REMOVING');
