-- 관리자 수기 등록(ADMIN_ENTRY) 건의 출처(공개 커뮤니티·블로그 글의 URL 또는 메모).
-- 사용자 제보(USER_REPORT)·DEMO·기타 출처는 null로 남는다 — 이 컬럼은 ADMIN_ENTRY 전용이다.
ALTER TABLE passer_data
    ADD COLUMN source_note TEXT;
