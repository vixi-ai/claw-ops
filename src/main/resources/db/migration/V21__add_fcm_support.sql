CREATE TABLE device_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token       VARCHAR(512) NOT NULL,
    platform    VARCHAR(30)  NOT NULL DEFAULT 'WEB',
    user_id     UUID         REFERENCES users(id) ON DELETE SET NULL,
    provider_id UUID         NOT NULL REFERENCES notification_providers(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (token, provider_id)
);

CREATE INDEX idx_device_token_provider ON device_tokens(provider_id);
