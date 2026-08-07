-- 활동별 실제 선발자 데이터뿐 아니라 직무별 합성 데모 데이터를 저장할 수 있게 한다.
ALTER TABLE passer_data
    ALTER COLUMN activity_id DROP NOT NULL,
    ADD COLUMN data_origin VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE passer_data
    ADD CONSTRAINT chk_passer_data_origin
        CHECK (data_origin IN ('DEMO', 'PUBLIC_REVIEW', 'USER_REPORT', 'UNKNOWN'));
