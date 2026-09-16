-- 합격자 제보 검수 이력. 승인(is_verified=true)과 반려를 구분하고 누가 언제 검수했는지 남긴다.
-- 반려 데이터는 삭제하지 않는다 — 같은 사용자의 재제보·중복 제보 판단과 감사에 쓴다.
ALTER TABLE passer_data
    ADD COLUMN reviewed_at TIMESTAMP,
    ADD COLUMN reviewed_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN reject_reason VARCHAR(300);

-- 검수 대기 목록 조회용: 미검수 사용자 제보만 빠르게 찾는다.
CREATE INDEX idx_passer_data_pending_review
    ON passer_data (created_at DESC)
    WHERE data_origin = 'USER_REPORT' AND reviewed_at IS NULL;
