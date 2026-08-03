CREATE TABLE roadmap_caches (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    result_json JSONB NOT NULL,
    created_at TIMESTAMP,
    expires_at TIMESTAMP,
    CONSTRAINT fk_roadmap_caches_user FOREIGN KEY (user_id) REFERENCES users (id)
);
