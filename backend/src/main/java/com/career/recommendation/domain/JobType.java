package com.career.recommendation.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 서비스가 지원하는 직무 코드의 단일 정의.
 *
 * 2026-07-14 회의에서 확정한 6종이며, V13 마이그레이션으로 레거시 값(BE·FE·AI/ML·보안 등)은
 * 이미 이 코드로 정규화됐다. 예전에는 이 목록이 TargetJobRequest·PasserReportRequest의 regex,
 * PasserData 주석, 프론트 JobCode에 각각 따로 적혀 있어 한 곳만 바뀌면 나머지가 조용히 어긋났다.
 *
 * DB 컬럼(target_jobs.job_type, passer_data.job_type)은 문자열 그대로 두고, 코드에서는
 * 항상 이 enum을 거쳐서 읽고 쓴다. 레거시 별칭은 받지 않는다 — V13 이후 DB에 남아 있지 않고,
 * API 입력은 validator에서 막는다.
 */
public enum JobType {
    BACKEND("백엔드"),
    FRONTEND("프론트엔드"),
    DATA_ENGINEER("데이터 엔지니어"),
    AI_ML("AI/ML"),
    PM("기획/PM"),
    SECURITY("보안");

    private final String label;

    JobType(String label) {
        this.label = label;
    }

    /** 화면 표시용 한글 이름. */
    public String getLabel() {
        return label;
    }

    /**
     * 입력 문자열을 직무 코드로 해석한다. 앞뒤 공백과 대소문자는 관대하게 받지만
     * 그 외(레거시 별칭, 한글 라벨)는 거부한다.
     */
    public static Optional<JobType> from(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst();
    }

    /** {@link #from}과 같되 지원하지 않는 값이면 예외. 검증이 끝난 뒤의 서비스 코드에서 쓴다. */
    public static JobType of(String raw) {
        return from(raw).orElseThrow(() ->
                new IllegalArgumentException("지원하지 않는 직무 코드입니다: " + raw));
    }

    public static boolean isValid(String raw) {
        return from(raw).isPresent();
    }

    /**
     * DB에 저장된 문자열을 화면 라벨로 바꾼다. 알 수 없는 값이면 원문을 그대로 돌려줘서
     * 이상한 데이터가 들어와도 문구가 깨지지 않게 한다.
     */
    public static String labelOf(String code) {
        return from(code).map(JobType::getLabel).orElse(code);
    }
}
