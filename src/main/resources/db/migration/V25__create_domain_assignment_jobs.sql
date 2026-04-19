-- Domain assignment jobs: async DNS record creation + verification
CREATE TABLE domain_assignment_jobs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    domain_assignment_id    UUID NOT NULL REFERENCES domain_assignments(id) ON DELETE CASCADE,
    server_id               UUID REFERENCES servers(id) ON DELETE CASCADE,
    current_step            VARCHAR(30) NOT NULL DEFAULT 'PENDING_DNS',
    status                  VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    retry_count             INTEGER NOT NULL DEFAULT 0,
    max_retries             INTEGER NOT NULL DEFAULT 3,
    logs                    TEXT,
    error_message           TEXT,
    triggered_by            UUID,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    started_at              TIMESTAMP WITH TIME ZONE,
    finished_at             TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_dom_jobs_assignment ON domain_assignment_jobs(domain_assignment_id);
CREATE INDEX idx_dom_jobs_server ON domain_assignment_jobs(server_id);
CREATE INDEX idx_dom_jobs_status ON domain_assignment_jobs(status);
