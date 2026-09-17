package com.career.recommendation.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BACKLOG.md E11-2(1차)에서 확정한 직무별 요구 영역 체크리스트를 그대로 고정한다.
 * 이 값들은 팀 결정 사항이라 코드 리팩터링 과정에서 조용히 바뀌면 안 된다.
 */
class JobAreaRequirementsTest {

    @Test
    void BACKEND_요구_영역은_API_DB_AUTH_TEST_CI_CD_INFRA_순이다() {
        assertThat(JobAreaRequirements.requiredAreasFor(JobType.BACKEND))
                .containsExactly(ExperienceArea.API, ExperienceArea.DB, ExperienceArea.AUTH,
                        ExperienceArea.TEST, ExperienceArea.CI_CD, ExperienceArea.INFRA);
    }

    @Test
    void FRONTEND_요구_영역은_UI_STATE_MGMT_API_TEST_CI_CD_순이다() {
        assertThat(JobAreaRequirements.requiredAreasFor(JobType.FRONTEND))
                .containsExactly(ExperienceArea.UI, ExperienceArea.STATE_MGMT, ExperienceArea.API,
                        ExperienceArea.TEST, ExperienceArea.CI_CD);
    }

    @Test
    void DATA_ENGINEER_요구_영역은_DATA_PIPELINE_DB_INFRA_CI_CD_TEST_순이다() {
        assertThat(JobAreaRequirements.requiredAreasFor(JobType.DATA_ENGINEER))
                .containsExactly(ExperienceArea.DATA_PIPELINE, ExperienceArea.DB, ExperienceArea.INFRA,
                        ExperienceArea.CI_CD, ExperienceArea.TEST);
    }

    @Test
    void AI_ML_요구_영역은_ML_MODEL_DATA_PIPELINE_DB_TEST_순이다() {
        assertThat(JobAreaRequirements.requiredAreasFor(JobType.AI_ML))
                .containsExactly(ExperienceArea.ML_MODEL, ExperienceArea.DATA_PIPELINE,
                        ExperienceArea.DB, ExperienceArea.TEST);
    }

    @Test
    void PM_요구_영역은_PLANNING_DOCS_UI_순이다() {
        assertThat(JobAreaRequirements.requiredAreasFor(JobType.PM))
                .containsExactly(ExperienceArea.PLANNING, ExperienceArea.DOCS, ExperienceArea.UI);
    }

    @Test
    void SECURITY_요구_영역은_SECURITY_AUTH_API_TEST_INFRA_순이다() {
        assertThat(JobAreaRequirements.requiredAreasFor(JobType.SECURITY))
                .containsExactly(ExperienceArea.SECURITY, ExperienceArea.AUTH, ExperienceArea.API,
                        ExperienceArea.TEST, ExperienceArea.INFRA);
    }

    @Test
    void jobType이_null이면_빈_리스트다() {
        assertThat(JobAreaRequirements.requiredAreasFor(null)).isEmpty();
    }
}
