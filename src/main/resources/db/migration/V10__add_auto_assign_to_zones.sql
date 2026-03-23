ALTER TABLE managed_zones ADD COLUMN default_for_auto_assign BOOLEAN NOT NULL DEFAULT FALSE;

-- Partial unique index: only one zone can be the default for auto-assign
CREATE UNIQUE INDEX idx_managed_zones_auto_assign_default
    ON managed_zones (default_for_auto_assign)
    WHERE default_for_auto_assign = TRUE;
