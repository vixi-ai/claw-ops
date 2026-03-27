ALTER TABLE deployment_jobs ADD COLUMN interactive BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE deployment_jobs ADD COLUMN terminal_session_id VARCHAR(64);
