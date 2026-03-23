-- Make ip_address optional (host can be hostname OR IP in the hostname column)
ALTER TABLE servers ALTER COLUMN ip_address DROP NOT NULL;

-- Set default environment to 'production'
ALTER TABLE servers ALTER COLUMN environment SET DEFAULT 'production';

-- Migrate: if hostname is empty but ip_address is set, copy ip to hostname
UPDATE servers SET hostname = ip_address WHERE (hostname IS NULL OR hostname = '') AND ip_address IS NOT NULL AND ip_address != '';
