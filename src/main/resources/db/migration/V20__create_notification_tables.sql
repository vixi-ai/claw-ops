-- Notification providers (multi-provider, one default)
CREATE TABLE notification_providers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_type   VARCHAR(30)  NOT NULL,
    display_name    VARCHAR(100) NOT NULL UNIQUE,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    credential_id   UUID         REFERENCES secrets(id) ON DELETE SET NULL,
    provider_settings TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Ensure at most one default provider
CREATE UNIQUE INDEX uq_notification_provider_default
    ON notification_providers (is_default) WHERE is_default = TRUE;

-- Web push subscriptions
CREATE TABLE push_subscriptions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint    VARCHAR(512) NOT NULL UNIQUE,
    key_auth    VARCHAR(255) NOT NULL,
    key_p256dh  VARCHAR(255) NOT NULL,
    user_id     UUID         REFERENCES users(id) ON DELETE SET NULL,
    provider_id UUID         NOT NULL REFERENCES notification_providers(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_push_sub_provider ON push_subscriptions(provider_id);
