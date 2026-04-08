-- User devices — tracks every device a user has registered for notifications
CREATE TABLE user_devices (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_name           VARCHAR(100) NOT NULL,
    platform              VARCHAR(30)  NOT NULL DEFAULT 'WEB',
    notifications_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_device_user ON user_devices(user_id);

-- Link push_subscriptions and device_tokens to a user device
ALTER TABLE push_subscriptions ADD COLUMN device_id UUID REFERENCES user_devices(id) ON DELETE SET NULL;
ALTER TABLE device_tokens      ADD COLUMN device_id UUID REFERENCES user_devices(id) ON DELETE SET NULL;
