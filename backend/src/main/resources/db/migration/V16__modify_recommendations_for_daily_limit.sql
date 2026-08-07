ALTER TABLE recommendations
    DROP COLUMN expires_at,
    ADD COLUMN daily_update_count INT DEFAULT 0 NOT NULL,
    ADD COLUMN last_updated_date DATE;
