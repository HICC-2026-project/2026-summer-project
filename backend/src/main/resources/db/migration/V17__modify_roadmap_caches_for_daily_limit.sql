ALTER TABLE roadmap_caches
    DROP COLUMN IF EXISTS expires_at,
    ADD COLUMN IF NOT EXISTS daily_update_count INT DEFAULT 0 NOT NULL,
    ADD COLUMN IF NOT EXISTS last_updated_date DATE;
