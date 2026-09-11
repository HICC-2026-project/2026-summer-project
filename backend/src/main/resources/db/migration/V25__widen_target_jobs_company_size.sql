-- V11은 company_size를 VARCHAR(50)으로 넓히려 했지만 ADD COLUMN IF NOT EXISTS라서 V3의 VARCHAR(20) 컬럼이
-- 그대로 남아 있었다(TargetJobRequest 주석의 알려진 한계). 실제로 넓힌다 — industry(50)와 같은 폭.
ALTER TABLE target_jobs
    ALTER COLUMN company_size TYPE VARCHAR(50);
