ALTER TABLE passer_data
    ADD COLUMN IF NOT EXISTS job_type VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_passer_job_type ON passer_data(job_type);
