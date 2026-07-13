CREATE TABLE activities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type            VARCHAR(20) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    organization    VARCHAR(100),
    description     TEXT,
    deadline        DATE,
    start_date      DATE,
    end_date        DATE,
    target_spec     JSONB,
    tags            TEXT[] DEFAULT '{}',
    url             VARCHAR(500),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_activities_type ON activities(type);
CREATE INDEX idx_activities_deadline ON activities(deadline);
