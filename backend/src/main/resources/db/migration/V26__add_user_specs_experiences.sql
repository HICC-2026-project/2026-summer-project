-- user_specs에 경험 목록(jsonb) 컬럼을 추가한다. language_scores와 같은 패턴이다.
-- nullable, 기본값 없음 — 기존 행은 NULL로 남아 "미입력"으로 취급된다(빈 배열=0개와 구분).
ALTER TABLE user_specs ADD COLUMN experiences jsonb;
