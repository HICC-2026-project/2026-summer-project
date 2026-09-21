package com.career.recommendation.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Activity.targetSpec 허용 키·타입을 고정한다. backend/seed/linkareer-activity-seed-2026-09-17.sql의
 * 실제 target_spec 샘플 몇 건을 하드코딩해, 크롤러 산출물이 스키마를 벗어나지 않는지도 함께 확인한다.
 */
class TargetSpecSchemaTest {

    @Test
    void null이거나_빈_맵이면_위반이_없다() {
        assertThat(TargetSpecSchema.validate(null)).isEmpty();
        assertThat(TargetSpecSchema.validate(Map.of())).isEmpty();
    }

    @Test
    void required_qualifications가_문자열_리스트면_위반이_없다() {
        Map<String, Object> targetSpec = Map.of(
                "required_qualifications", List.of("국내외 대학 학사학위 이상 소지자", "기졸업자 및 27년 2월 이내 졸업 예정자"));

        assertThat(TargetSpecSchema.validate(targetSpec)).isEmpty();
    }

    @Test
    void 허용되지_않은_키는_위반이다() {
        Map<String, Object> targetSpec = Map.of("preferred_qualifications", List.of("우대사항"));

        assertThat(TargetSpecSchema.validate(targetSpec))
                .containsExactly("알 수 없는 키: preferred_qualifications");
    }

    @Test
    void required_qualifications_값이_문자열_리스트가_아니면_위반이다() {
        Map<String, Object> notAList = Map.of("required_qualifications", "학사 학위 이상");
        Map<String, Object> listWithNonString = Map.of("required_qualifications", List.of("정상 항목", 20));

        assertThat(TargetSpecSchema.validate(notAList))
                .containsExactly("required_qualifications은(는) 문자열 리스트여야 합니다.");
        assertThat(TargetSpecSchema.validate(listWithNonString))
                .containsExactly("required_qualifications은(는) 문자열 리스트여야 합니다.");
    }

    @Test
    void 알수없는_키와_타입_위반이_섞이면_둘_다_보고한다() {
        Map<String, Object> targetSpec = Map.of(
                "required_qualifications", "학사 학위 이상",
                "extra", List.of("아무 값"));

        assertThat(TargetSpecSchema.validate(targetSpec))
                .containsExactlyInAnyOrder(
                        "required_qualifications은(는) 문자열 리스트여야 합니다.",
                        "알 수 없는 키: extra");
    }

    // ── 시드 SQL 샘플 (backend/seed/linkareer-activity-seed-2026-09-17.sql) ────────────

    @Test
    void 시드_샘플_CJ_공채_target_spec은_스키마를_통과한다() {
        Map<String, Object> targetSpec = Map.of("required_qualifications", List.of(
                "(공통) 국내·외 학사 학위 이상 소지자",
                "기졸업자 및 2027년 2월 이내 졸업 예정자",
                "병역필 또는 면제로 해외여행에 결격사유가 없는 분에 한하여 지원 가능합니다."));

        assertThat(TargetSpecSchema.validate(targetSpec)).isEmpty();
    }

    @Test
    void 시드_샘플_학력무관_교육과정_target_spec은_스키마를_통과한다() {
        Map<String, Object> targetSpec = Map.of("required_qualifications", List.of(
                "아래 중 1가지라도 해당되시면 교육 대상자 입니다.",
                "초보자, 비전공자도 가능",
                "고등학교 졸업이상",
                "전문대, 4년제 대학 졸업예정자"));

        assertThat(TargetSpecSchema.validate(targetSpec)).isEmpty();
    }

    @Test
    void 시드_샘플_빈_target_spec도_스키마를_통과한다() {
        // 자격 요건 섹션을 못 찾은 활동은 크롤러가 '{}'::jsonb로 저장한다(예: ICT-SW 여성창업공모전).
        assertThat(TargetSpecSchema.validate(Map.of())).isEmpty();
    }
}
