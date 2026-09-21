-- E10-2(F-09) 추천 피드백: 활동별 좋아요/싫어요. 사용자당 활동 하나에 반응은 1개뿐이고
-- (UNIQUE(user_id, activity_id)), 다시 누르면 서비스가 upsert하고 같은 반응을 재클릭하면
-- 행 자체를 지워 해제한다(별도 "NONE" 상태를 두지 않는다).
--
-- 7/14 "유저당 1건 + 24시간 캐시" 결정은 유지한다 — 피드백을 남겨도 recommendations 캐시를
-- 즉시 재생성하지 않고, 다음 갱신(스펙 변경 등) 때 PromptDataBuilder가 이 테이블을 반영한다.
--
-- user_id는 V22가 recommendations.user_id에 적용한 것과 동일하게 CASCADE — 회원 탈퇴 시
-- 함께 지워져야 하는 개인 파생 데이터다. activity_id도 같은 이유로 CASCADE — 활동이 지워지면
-- 그 활동에 대한 피드백은 더 이상 의미가 없고, 관리자 집계(활동별 like/dislike)에서도 고아
-- 행으로 남을 이유가 없다(V5 passer_data처럼 활동 삭제를 막는 RESTRICT는 여기선 맞지 않는다 —
-- passer_data는 활동이 지워져도 보존해야 할 합격 스펙 기록이지만, 피드백은 그 활동에 대한
-- 의견일 뿐이라 활동과 생명주기를 같이한다).
CREATE TABLE recommendation_feedback (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    activity_id UUID NOT NULL REFERENCES activities(id) ON DELETE CASCADE,
    reaction    VARCHAR(10) NOT NULL CHECK (reaction IN ('LIKE', 'DISLIKE')),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, activity_id)
);

-- 관리자 집계(활동별 like/dislike 수)와 프롬프트 주입(사용자별 최근 반응 조회)이 각각 이 순서로 찾는다.
CREATE INDEX idx_recommendation_feedback_activity ON recommendation_feedback(activity_id);
CREATE INDEX idx_recommendation_feedback_user_reaction ON recommendation_feedback(user_id, reaction, updated_at DESC);
