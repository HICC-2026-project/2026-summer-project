package com.career.recommendation.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * E11(1단계) — 경험 항목이 다룬 "기여 영역" 코드의 단일 정의. JobType과 같은 패턴(코드+한글
 * 라벨+대소문자 관대한 from(String))을 따른다.
 *
 * GitHub 파생 경험은 {@link com.career.recommendation.util.JobSignalClassifier}가 레포 신호로
 * 자동 채우고(PLANNING 제외 — 코드 신호로는 식별하지 않는다), 수동 경험은 사용자가 직접
 * 고른다. 어떤 값도 점수(percentile)에는 반영하지 않는다 — 화면에 칩으로만 보여준다.
 */
public enum ExperienceArea {
    AUTH("인증"),
    API("API 개발"),
    DB("데이터베이스"),
    CI_CD("CI/CD"),
    TEST("테스트"),
    UI("UI 구현"),
    STATE_MGMT("상태 관리"),
    DATA_PIPELINE("데이터 파이프라인"),
    ML_MODEL("ML 모델"),
    INFRA("인프라/배포"),
    DOCS("문서화"),
    SECURITY("보안 분석"),
    PLANNING("기획");

    private final String label;

    ExperienceArea(String label) {
        this.label = label;
    }

    /** 화면 표시용 한글 이름. */
    public String getLabel() {
        return label;
    }

    /**
     * 입력 문자열을 기여 영역 코드로 해석한다. 앞뒤 공백과 대소문자는 관대하게 받지만
     * 그 외(알 수 없는 코드, 한글 라벨)는 거부한다.
     */
    public static Optional<ExperienceArea> from(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(area -> area.name().equals(normalized))
                .findFirst();
    }

    public static boolean isValid(String raw) {
        return from(raw).isPresent();
    }

    /**
     * DB에 저장된 문자열을 화면 라벨로 바꾼다. 알 수 없는 값이면 원문을 그대로 돌려줘서
     * 이상한 데이터가 들어와도 문구가 깨지지 않게 한다.
     */
    public static String labelOf(String code) {
        return from(code).map(ExperienceArea::getLabel).orElse(code);
    }
}
