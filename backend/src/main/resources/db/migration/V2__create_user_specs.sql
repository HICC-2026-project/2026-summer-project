CREATE TABLE user_specs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gpa                 DECIMAL(3,2),
    gpa_max             DECIMAL(3,2) DEFAULT 4.5,
    language_score      JSONB,
    certifications      TEXT[] DEFAULT '{}',
    experiences         TEXT[] DEFAULT '{}',
    github_url          VARCHAR(255),
    portfolio_url       VARCHAR(255),
    updated_at          TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id)
);
