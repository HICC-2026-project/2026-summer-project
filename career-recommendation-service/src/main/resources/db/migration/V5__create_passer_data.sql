CREATE TABLE passer_data (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_id      UUID NOT NULL REFERENCES activities(id),
    year             INTEGER,
    gpa              DECIMAL(3,2),
    language_score   JSONB,
    certifications   TEXT[] DEFAULT '{}',
    experience_count INTEGER DEFAULT 0,
    spec_summary     TEXT,
    is_verified      BOOLEAN DEFAULT FALSE,
    created_at       TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_passer_gpa ON passer_data(gpa);
