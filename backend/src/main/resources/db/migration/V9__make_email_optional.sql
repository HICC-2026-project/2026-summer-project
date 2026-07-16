ALTER TABLE users
    ALTER COLUMN email DROP NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT uq_users_provider_provider_id UNIQUE (provider, provider_id);
