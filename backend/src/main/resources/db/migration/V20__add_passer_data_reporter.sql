-- 합격자 제보에 제보자(users.id)를 연결한다.
-- 용도: 본인 제보 상태 조회(GET /passers/reports/me), 중복 제보 제한(E2-3).
-- 비교·추천 응답에는 절대 노출하지 않는다 — 합격자 데이터의 익명성은 "외부로 나가는
-- 응답" 기준이고, 제보자 연결은 검수·운영을 위한 내부 정보다.
-- DEMO·PUBLIC_REVIEW 데이터와 탈퇴한 사용자의 제보는 NULL(ON DELETE SET NULL)로 남겨
-- 데이터 자체는 유지한다.
ALTER TABLE passer_data
    ADD COLUMN reporter_user_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_passer_data_reporter_user_id
    ON passer_data (reporter_user_id)
    WHERE reporter_user_id IS NOT NULL;
