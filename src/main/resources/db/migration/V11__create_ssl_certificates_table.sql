CREATE TABLE ssl_certificates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id       UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    assignment_id   UUID REFERENCES domain_assignments(id) ON DELETE SET NULL,
    hostname        VARCHAR(255) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    admin_email     VARCHAR(255),
    target_port     INTEGER NOT NULL DEFAULT 3000,
    expires_at      TIMESTAMP WITH TIME ZONE,
    last_renewed_at TIMESTAMP WITH TIME ZONE,
    last_error      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_ssl_certificates_server_id ON ssl_certificates(server_id);
CREATE INDEX idx_ssl_certificates_hostname ON ssl_certificates(hostname);
CREATE INDEX idx_ssl_certificates_status ON ssl_certificates(status);
