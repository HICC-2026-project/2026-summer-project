-- 합격자 제보 검수에 사용할 증빙 이미지의 로컬 저장 메타데이터.
-- 기존 DEMO/PUBLIC_REVIEW 데이터에는 증빙이 없으므로 nullable로 유지한다.
ALTER TABLE passer_data
    ADD COLUMN proof_original_name VARCHAR(255),
    ADD COLUMN proof_stored_name VARCHAR(100),
    ADD COLUMN proof_content_type VARCHAR(50),
    ADD COLUMN proof_file_size BIGINT;

ALTER TABLE passer_data
    ADD CONSTRAINT chk_passer_proof_file_size
        CHECK (proof_file_size IS NULL OR proof_file_size >= 0);
