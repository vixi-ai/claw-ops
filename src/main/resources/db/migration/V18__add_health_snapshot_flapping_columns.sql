-- ============================================================
-- V18: Add flapping protection columns to health_snapshots
-- ============================================================

ALTER TABLE health_snapshots
    ADD COLUMN proposed_state VARCHAR(20),
    ADD COLUMN consecutive_in_proposed INT NOT NULL DEFAULT 0,
    ADD COLUMN previous_state VARCHAR(20),
    ADD COLUMN state_changed_at TIMESTAMPTZ;
