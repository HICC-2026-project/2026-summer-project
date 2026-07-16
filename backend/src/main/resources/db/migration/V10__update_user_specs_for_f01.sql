ALTER TABLE user_specs
    ADD COLUMN IF NOT EXISTS language_scores JSONB;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'user_specs'
          AND column_name = 'language_score'
    ) THEN
UPDATE user_specs
SET language_scores = jsonb_build_array(language_score)
WHERE language_score IS NOT NULL
  AND language_scores IS NULL;
END IF;
END $$;

ALTER TABLE user_specs
DROP COLUMN IF EXISTS language_score;

ALTER TABLE user_specs
DROP COLUMN IF EXISTS experiences;

ALTER TABLE user_specs
DROP COLUMN IF EXISTS github_url;

ALTER TABLE user_specs
DROP COLUMN IF EXISTS portfolio_url;

ALTER TABLE user_specs
    ADD COLUMN IF NOT EXISTS grade INTEGER;

ALTER TABLE user_specs
ALTER COLUMN gpa TYPE NUMERIC(4, 2);

ALTER TABLE user_specs
ALTER COLUMN gpa_max TYPE NUMERIC(4, 2);