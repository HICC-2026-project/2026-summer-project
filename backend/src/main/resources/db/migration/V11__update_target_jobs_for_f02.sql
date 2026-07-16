CREATE TABLE IF NOT EXISTS target_jobs (
                                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_type VARCHAR(50),
    company_size VARCHAR(50),
    industry VARCHAR(50),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id)
    );

ALTER TABLE target_jobs
    ADD COLUMN IF NOT EXISTS job_type VARCHAR(50);

ALTER TABLE target_jobs
    ADD COLUMN IF NOT EXISTS company_size VARCHAR(50);

ALTER TABLE target_jobs
    ADD COLUMN IF NOT EXISTS industry VARCHAR(50);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'target_jobs'
          AND column_name = 'job_role'
    ) THEN
UPDATE target_jobs
SET job_type = job_role
WHERE job_type IS NULL
  AND job_role IS NOT NULL;
END IF;
END $$;

ALTER TABLE target_jobs
DROP COLUMN IF EXISTS job_role;

ALTER TABLE target_jobs
DROP COLUMN IF EXISTS industries;