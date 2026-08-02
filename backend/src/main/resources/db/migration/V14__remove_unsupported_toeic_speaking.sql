-- UserSpec과 PasserData의 지원 어학시험을 TOEIC, TOEFL, OPIC으로 통일한다.
-- 다른 어학 성적은 유지하고 TOEIC_SPEAKING 항목만 배열에서 제거한다.
UPDATE passer_data
SET language_scores = COALESCE(
        (
            SELECT jsonb_agg(score)
            FROM jsonb_array_elements(language_scores) AS score
            WHERE UPPER(score ->> 'type') <> 'TOEIC_SPEAKING'
        ),
        '[]'::jsonb
    )
WHERE language_scores IS NOT NULL
  AND jsonb_typeof(language_scores) = 'array'
  AND EXISTS (
      SELECT 1
      FROM jsonb_array_elements(language_scores) AS score
      WHERE UPPER(score ->> 'type') = 'TOEIC_SPEAKING'
  );
