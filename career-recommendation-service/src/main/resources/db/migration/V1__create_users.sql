CREATE EXTENSION IF NOT EXISTS pgcrypto;


CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) UNIQUE NOT NULL,
    nickname    VARCHAR(50),
    provider    VARCHAR(20) NOT NULL,
    provider_id VARCHAR(100) NOT NULL,
    role        VARCHAR(20) DEFAULT 'USER',
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);
