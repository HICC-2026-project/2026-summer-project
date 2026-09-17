package com.career.recommendation.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * E11-2(1차) — 직무별 요구 영역 고정 체크리스트.
 *
 * BACKLOG.md E11-2에서 확정한 팀 결정이다("1차: 팀 정의 체크리스트 JobAreaRequirements
 * (6직무 × 5~7영역)") — 코드에서 임의로 구성·순서를 바꾸지 않는다. 2차(합격자 areas 분포
 * 기반, "BACKEND 합격자 80%가 AUTH 보유" 같은 실측 비율)로 대체되기 전까지의 임시 기준이며,
 * 점수(percentile)에는 전혀 반영하지 않는다 — 커버리지 표시·추천/로드맵 프롬프트 전용.
 *
 * 선언 순서가 곧 GET /recommendations 응답 specPosition.areaCoverage 배열의 순서다
 * (FE와 확정된 계약 — SpecPositionCalculator 참고).
 */
public final class JobAreaRequirements {

    private static final Map<JobType, List<ExperienceArea>> REQUIREMENTS = new EnumMap<>(JobType.class);

    static {
        REQUIREMENTS.put(JobType.BACKEND, List.of(
                ExperienceArea.API, ExperienceArea.DB, ExperienceArea.AUTH,
                ExperienceArea.TEST, ExperienceArea.CI_CD, ExperienceArea.INFRA));
        REQUIREMENTS.put(JobType.FRONTEND, List.of(
                ExperienceArea.UI, ExperienceArea.STATE_MGMT, ExperienceArea.API,
                ExperienceArea.TEST, ExperienceArea.CI_CD));
        REQUIREMENTS.put(JobType.DATA_ENGINEER, List.of(
                ExperienceArea.DATA_PIPELINE, ExperienceArea.DB, ExperienceArea.INFRA,
                ExperienceArea.CI_CD, ExperienceArea.TEST));
        REQUIREMENTS.put(JobType.AI_ML, List.of(
                ExperienceArea.ML_MODEL, ExperienceArea.DATA_PIPELINE, ExperienceArea.DB,
                ExperienceArea.TEST));
        REQUIREMENTS.put(JobType.PM, List.of(
                ExperienceArea.PLANNING, ExperienceArea.DOCS, ExperienceArea.UI));
        REQUIREMENTS.put(JobType.SECURITY, List.of(
                ExperienceArea.SECURITY, ExperienceArea.AUTH, ExperienceArea.API,
                ExperienceArea.TEST, ExperienceArea.INFRA));
    }

    private JobAreaRequirements() {
    }

    /** 이 직무가 요구하는 영역 목록(선언 순서 유지). 정의가 없는 직무(현재는 없음)는 빈 리스트. */
    public static List<ExperienceArea> requiredAreasFor(JobType jobType) {
        if (jobType == null) {
            return List.of();
        }
        return REQUIREMENTS.getOrDefault(jobType, List.of());
    }
}
