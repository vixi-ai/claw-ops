-- Reassign device_tokens from duplicate user_devices to the keeper (newest per user+name)
UPDATE device_tokens dt
SET device_id = keeper.id
FROM user_devices dup
JOIN (
    SELECT DISTINCT ON (user_id, device_name) id, user_id, device_name
    FROM user_devices
    ORDER BY user_id, device_name, created_at DESC
) keeper ON keeper.user_id = dup.user_id AND keeper.device_name = dup.device_name AND keeper.id != dup.id
WHERE dt.device_id = dup.id;

-- Reassign push_subscriptions from duplicates to keeper
UPDATE push_subscriptions ps
SET device_id = keeper.id
FROM user_devices dup
JOIN (
    SELECT DISTINCT ON (user_id, device_name) id, user_id, device_name
    FROM user_devices
    ORDER BY user_id, device_name, created_at DESC
) keeper ON keeper.user_id = dup.user_id AND keeper.device_name = dup.device_name AND keeper.id != dup.id
WHERE ps.device_id = dup.id;

-- Delete duplicate user_devices (keep newest per user+name)
DELETE FROM user_devices
WHERE id NOT IN (
    SELECT DISTINCT ON (user_id, device_name) id
    FROM user_devices
    ORDER BY user_id, device_name, created_at DESC
);

-- Prevent future duplicates
ALTER TABLE user_devices ADD CONSTRAINT uq_user_device_user_name UNIQUE (user_id, device_name);
