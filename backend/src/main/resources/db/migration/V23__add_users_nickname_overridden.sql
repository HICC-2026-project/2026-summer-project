-- 사용자가 앱에서 직접 바꾼 닉네임을 카카오 로그인이 덮어쓰지 않게 하는 플래그.
-- (OAuth2LoginSuccessHandler.upsertUser는 로그인마다 카카오 닉네임을 복사한다)
ALTER TABLE users
    ADD COLUMN nickname_overridden BOOLEAN NOT NULL DEFAULT FALSE;
