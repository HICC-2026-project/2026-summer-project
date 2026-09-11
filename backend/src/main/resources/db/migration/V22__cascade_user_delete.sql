-- 회원 탈퇴(DELETE /users/me) 준비: users 행을 지우면 개인 파생 데이터가 함께 지워지게 한다.
-- user_specs·target_jobs·refresh_tokens는 처음부터 ON DELETE CASCADE였지만
-- recommendations(V6)·roadmap_caches(V15)는 빠져 있어 탈퇴가 FK 위반으로 실패했다.
-- passer_data의 reporter/reviewed_by는 SET NULL(V20·V21) — 익명 합격자 데이터는 보존한다.
ALTER TABLE recommendations
    DROP CONSTRAINT recommendations_user_id_fkey,
    ADD CONSTRAINT recommendations_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE roadmap_caches
    DROP CONSTRAINT fk_roadmap_caches_user,
    ADD CONSTRAINT fk_roadmap_caches_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
