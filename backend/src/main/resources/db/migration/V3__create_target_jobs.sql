CREATE TABLE target_jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_type        VARCHAR(50) NOT NULL,
    company_size    VARCHAR(20),
    industry        VARCHAR(50),
    updated_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id)
);
