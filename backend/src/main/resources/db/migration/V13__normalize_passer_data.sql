ALTER TABLE passer_data
    ALTER COLUMN gpa TYPE DECIMAL(4,2),
    ADD COLUMN gpa_max DECIMAL(4,2),
    ADD COLUMN language_scores JSONB DEFAULT '[]'::jsonb;

-- 기존 MatchScoreCalculator가 합격자 학점을 4.5 만점으로 간주해 온 데이터를 명시적으로 이관한다.
UPDATE passer_data
SET gpa_max = 4.5
WHERE gpa IS NOT NULL
  AND gpa_max IS NULL;

-- 기존 단일 JSON 객체를 UserSpec과 같은 배열 구조로 변환한다.
UPDATE passer_data
SET language_scores = CASE
    WHEN language_score IS NULL THEN '[]'::jsonb
    WHEN jsonb_typeof(language_score) = 'array' THEN language_score
    WHEN language_score ? 'type' THEN jsonb_build_array(language_score)
    WHEN language_score ? 'toeic' THEN jsonb_build_array(
            jsonb_build_object(
                    'type', 'TOEIC',
                    'score', (language_score ->> 'toeic')::INTEGER,
                    'maxScore', 990
            )
    )
    WHEN language_score ? 'toefl' THEN jsonb_build_array(
            jsonb_build_object(
                    'type', 'TOEFL',
                    'score', (language_score ->> 'toefl')::INTEGER,
                    'maxScore', 120
            )
    )
    WHEN language_score ? 'opic' THEN jsonb_build_array(
            jsonb_build_object(
                    'type', 'OPIC',
                    'grade', UPPER(language_score ->> 'opic')
            )
    )
    WHEN language_score ? 'toeic_speaking' THEN jsonb_build_array(
            jsonb_build_object(
                    'type', 'TOEIC_SPEAKING',
                    'grade', UPPER(language_score ->> 'toeic_speaking')
            )
    )
    ELSE '[]'::jsonb
END;

ALTER TABLE passer_data
    DROP COLUMN language_score;

-- 서버와 합격자 데이터가 동일한 직무 코드를 사용하도록 기존 값을 변환한다.
UPDATE passer_data SET job_type = 'BACKEND' WHERE job_type = 'BE';
UPDATE passer_data SET job_type = 'FRONTEND' WHERE job_type = 'FE';
UPDATE passer_data SET job_type = 'AI_ML' WHERE job_type = 'AI/ML';
UPDATE passer_data SET job_type = 'DATA_ENGINEER' WHERE job_type IN ('데이터', '데이터 엔지니어');
UPDATE passer_data SET job_type = 'PM' WHERE job_type = '기획/PM';
UPDATE passer_data SET job_type = 'SECURITY' WHERE job_type = '보안';

UPDATE target_jobs SET job_type = 'AI_ML' WHERE job_type = 'AI/ML';
UPDATE target_jobs SET job_type = 'DATA_ENGINEER' WHERE job_type = '데이터 엔지니어';
UPDATE target_jobs SET job_type = 'PM' WHERE job_type = '기획/PM';
UPDATE target_jobs SET job_type = 'SECURITY' WHERE job_type = '보안';

CREATE INDEX idx_passer_gpa_ratio
    ON passer_data ((gpa / NULLIF(gpa_max, 0)))
    WHERE gpa IS NOT NULL AND gpa_max IS NOT NULL;
