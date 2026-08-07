-- 발표 및 개발 검증 전용 합성 PasserData 30건.
-- 실제 개인의 합격 기록이 아니며, 개인정보와 특정 기업 합격 여부를 포함하지 않는다.
-- JobKorea의 공개 기업별 합격 스펙 집계는 값의 대략적인 범위를 정하는 참고 자료로만 사용했다.
-- 상세 산정 원칙과 출처: backend/seed/passer-demo-seed.README.md
-- V18 적용 후 수동 실행한다. 고정 UUID와 ON CONFLICT로 여러 번 실행해도 중복되지 않는다.

INSERT INTO passer_data (
    id, activity_id, job_type, year, gpa, gpa_max, language_scores,
    certifications, experience_count, spec_summary, is_verified, data_origin
)
VALUES
-- BACKEND
('d0000000-0000-0000-0001-000000000001', NULL, 'BACKEND', 2024, 3.42, 4.50, '[{"type":"TOEIC","score":760,"maxScore":990}]'::jsonb, ARRAY['정보처리기사'], 1, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0001-000000000002', NULL, 'BACKEND', 2025, 3.56, 4.50, '[{"type":"OPIC","grade":"IM2"}]'::jsonb, ARRAY['SQLD'], 2, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0001-000000000003', NULL, 'BACKEND', 2025, 3.67, 4.50, '[{"type":"TOEIC","score":820,"maxScore":990}]'::jsonb, ARRAY['정보처리기사','SQLD'], 2, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0001-000000000004', NULL, 'BACKEND', 2026, 3.78, 4.50, '[{"type":"OPIC","grade":"IH"}]'::jsonb, ARRAY['정보처리기사','AWS SAA'], 3, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0001-000000000005', NULL, 'BACKEND', 2026, 3.91, 4.50, '[{"type":"TOEFL","score":103,"maxScore":120}]'::jsonb, ARRAY['정보처리기사','SQLD','AWS SAA'], 3, NULL, FALSE, 'DEMO'),

-- FRONTEND
('d0000000-0000-0000-0002-000000000001', NULL, 'FRONTEND', 2024, 3.35, 4.50, '[{"type":"TOEIC","score":735,"maxScore":990}]'::jsonb, ARRAY['웹디자인기능사'], 1, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0002-000000000002', NULL, 'FRONTEND', 2025, 3.51, 4.50, '[{"type":"OPIC","grade":"IM2"}]'::jsonb, ARRAY['SQLD'], 2, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0002-000000000003', NULL, 'FRONTEND', 2025, 3.63, 4.50, '[{"type":"TOEIC","score":805,"maxScore":990}]'::jsonb, ARRAY['정보처리기사'], 2, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0002-000000000004', NULL, 'FRONTEND', 2026, 3.74, 4.50, '[{"type":"OPIC","grade":"IH"}]'::jsonb, ARRAY['정보처리기사','SQLD'], 3, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0002-000000000005', NULL, 'FRONTEND', 2026, 3.88, 4.50, '[{"type":"TOEFL","score":99,"maxScore":120}]'::jsonb, ARRAY['정보처리기사','웹디자인기능사'], 3, NULL, FALSE, 'DEMO'),

-- AI_ML
('d0000000-0000-0000-0003-000000000001', NULL, 'AI_ML', 2024, 3.48, 4.50, '[{"type":"TOEIC","score":780,"maxScore":990}]'::jsonb, ARRAY['ADsP'], 1, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0003-000000000002', NULL, 'AI_ML', 2025, 3.62, 4.50, '[{"type":"OPIC","grade":"IM3"}]'::jsonb, ARRAY['ADsP','SQLD'], 2, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0003-000000000003', NULL, 'AI_ML', 2025, 3.73, 4.50, '[{"type":"TOEIC","score":835,"maxScore":990}]'::jsonb, ARRAY['정보처리기사','ADsP'], 2, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0003-000000000004', NULL, 'AI_ML', 2026, 3.86, 4.50, '[{"type":"OPIC","grade":"IH"}]'::jsonb, ARRAY['ADsP','빅데이터분석기사'], 3, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0003-000000000005', NULL, 'AI_ML', 2026, 4.02, 4.50, '[{"type":"TOEFL","score":106,"maxScore":120}]'::jsonb, ARRAY['정보처리기사','ADsP','빅데이터분석기사'], 3, NULL, FALSE, 'DEMO'),

-- DATA_ENGINEER
('d0000000-0000-0000-0004-000000000001', NULL, 'DATA_ENGINEER', 2024, 3.40, 4.50, '[{"type":"TOEIC","score":750,"maxScore":990}]'::jsonb, ARRAY['SQLD'], 1, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0004-000000000002', NULL, 'DATA_ENGINEER', 2025, 3.55, 4.50, '[{"type":"OPIC","grade":"IM2"}]'::jsonb, ARRAY['SQLD','ADsP'], 2, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0004-000000000003', NULL, 'DATA_ENGINEER', 2025, 3.68, 4.50, '[{"type":"TOEIC","score":825,"maxScore":990}]'::jsonb, ARRAY['정보처리기사','SQLD'], 2, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0004-000000000004', NULL, 'DATA_ENGINEER', 2026, 3.81, 4.50, '[{"type":"OPIC","grade":"IH"}]'::jsonb, ARRAY['SQLD','빅데이터분석기사'], 3, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0004-000000000005', NULL, 'DATA_ENGINEER', 2026, 3.95, 4.50, '[{"type":"TOEFL","score":101,"maxScore":120}]'::jsonb, ARRAY['정보처리기사','SQLD','AWS SAA'], 3, NULL, FALSE, 'DEMO'),

-- PM
('d0000000-0000-0000-0005-000000000001', NULL, 'PM', 2024, 3.31, 4.50, '[{"type":"TOEIC","score":770,"maxScore":990}]'::jsonb, ARRAY['SQLD'], 1, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0005-000000000002', NULL, 'PM', 2025, 3.49, 4.50, '[{"type":"OPIC","grade":"IM2"}]'::jsonb, ARRAY['ADsP'], 2, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0005-000000000003', NULL, 'PM', 2025, 3.61, 4.50, '[{"type":"TOEIC","score":830,"maxScore":990}]'::jsonb, ARRAY['SQLD','ADsP'], 2, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0005-000000000004', NULL, 'PM', 2026, 3.76, 4.50, '[{"type":"OPIC","grade":"IH"}]'::jsonb, ARRAY['정보처리기사','SQLD'], 3, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0005-000000000005', NULL, 'PM', 2026, 3.89, 4.50, '[{"type":"TOEFL","score":100,"maxScore":120}]'::jsonb, ARRAY['SQLD','ADsP','빅데이터분석기사'], 3, NULL, FALSE, 'DEMO'),

-- SECURITY
('d0000000-0000-0000-0006-000000000001', NULL, 'SECURITY', 2024, 3.38, 4.50, '[{"type":"TOEIC","score":745,"maxScore":990}]'::jsonb, ARRAY['리눅스마스터 2급'], 1, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0006-000000000002', NULL, 'SECURITY', 2025, 3.53, 4.50, '[{"type":"OPIC","grade":"IM2"}]'::jsonb, ARRAY['네트워크관리사 2급'], 2, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0006-000000000003', NULL, 'SECURITY', 2025, 3.66, 4.50, '[{"type":"TOEIC","score":815,"maxScore":990}]'::jsonb, ARRAY['정보처리기사','리눅스마스터 2급'], 2, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0006-000000000004', NULL, 'SECURITY', 2026, 3.80, 4.50, '[{"type":"OPIC","grade":"IH"}]'::jsonb, ARRAY['정보보안기사','네트워크관리사 2급'], 3, NULL, FALSE, 'DEMO'),
('d0000000-0000-0000-0006-000000000005', NULL, 'SECURITY', 2026, 3.94, 4.50, '[{"type":"TOEFL","score":102,"maxScore":120}]'::jsonb, ARRAY['정보처리기사','정보보안기사','리눅스마스터 2급'], 3, NULL, FALSE, 'DEMO')
ON CONFLICT (id) DO NOTHING;
