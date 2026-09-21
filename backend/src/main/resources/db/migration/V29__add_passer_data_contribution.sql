-- E11-5: 합격자 측 동의 기반 GitHub 수집. 합격자 제보(PasserReportRequest)에 GitHub 아이디를
-- 선택 입력하고 동의하면, 서버 토큰으로 공개 레포를 즉시(비동기) 분석해 파생값만 저장한다.
--
-- 저장 금지 원칙(BACKLOG E11 설계 원칙 "원본 미저장"과 동일): 레포명·URL·아이디는 어디에도
-- 남기지 않는다. areas/stack/github_derived는 전부 "결과"일 뿐 원본을 복원할 수 없는 집계값이다.
--
-- 세 컬럼 모두 nullable — GitHub 아이디를 입력하지 않거나 동의하지 않은 제보, 분석이 실패한
-- 제보는 그대로 null로 남고 제보 자체는 유효하다(E3의 GithubProfile과 달리 이 프로필 전용
-- 행을 따로 두지 않고 passer_data에 바로 얹는다 — 합격자 제보 1건당 최대 1회만 분석하면
-- 되므로 별도 상태 머신이 필요 없다).
ALTER TABLE passer_data
    ADD COLUMN areas TEXT[],
    ADD COLUMN stack TEXT[],
    -- 구조: {"repos": n, "commits": n, "activeMonths": n, "jobRatios": {"BACKEND": 0.6, ...}}
    ADD COLUMN github_derived JSONB;
