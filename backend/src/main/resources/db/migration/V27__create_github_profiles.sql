-- E3(1단계, OAuth 없음): GitHub 공개 레포 기반 경험 추론 결과를 저장한다.
-- user_id는 UNIQUE — 사용자당 GitHub 연결은 하나(재분석 시 이 행을 갱신).
CREATE TABLE github_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    username VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING|DONE|FAILED|RATE_LIMITED
    failure_reason VARCHAR(300),
    job_ratios JSONB,      -- [{"jobType":"BACKEND","ratio":0.62}, ..., {"jobType":"OTHER","ratio":0.1}]
    repos JSONB,           -- [{name, primaryJob, commits, files, firstCommitAt, lastCommitAt, mainLanguage}]
    commit_total INT,
    active_months INT,
    analyzed_at TIMESTAMP,
    requested_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
