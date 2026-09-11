-- 사용자·종류(RECOMMENDATION/ROADMAP)별 하루 Gemini 호출 "시도" 횟수.
-- 기존 recommendations.daily_update_count / roadmap_caches.daily_update_count 는 성공 저장만 세서
-- Gemini가 장애로 폴백만 반복하면 0에 머물러 하루 3회 게이트가 발동하지 않았다(코드 주석의 알려진 한계).
-- 성공·실패와 무관하게 시도 시점에 원자적으로(ON CONFLICT ... DO UPDATE) 올려 게이트가 이름값대로 동작하게 한다.
CREATE TABLE ai_daily_attempts (
    user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind          VARCHAR(20) NOT NULL,
    attempt_date  DATE        NOT NULL,
    attempt_count INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, kind, attempt_date)
);
