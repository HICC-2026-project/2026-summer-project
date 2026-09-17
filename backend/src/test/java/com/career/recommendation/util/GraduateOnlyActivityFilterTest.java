package com.career.recommendation.util;

import com.career.recommendation.entity.Activity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실사용 피드백(2026-09-17): 3학년 사용자에게 "학사 학위 이상/졸업예정자 전용" 대졸 공채가
 * 추천·로드맵 후보에 노출됐다. grade 1~3에게만 적용되는 결정적 사전 필터를 고정한다.
 */
class GraduateOnlyActivityFilterTest {

    private Activity activityWithTargetSpec(Map<String, Object> targetSpec) {
        return Activity.builder()
                .id(UUID.randomUUID())
                .type("EXTERNAL")
                .name("테스트 활동")
                .targetSpec(targetSpec)
                .build();
    }

    @Test
    void grade가_1_2_3이면_대졸_전용_키워드가_있는_활동을_제외한다() {
        Activity graduateOnly = activityWithTargetSpec(
                Map.of("required_qualifications", List.of("국내·외 학사 학위 이상 소지자")));
        Activity open = activityWithTargetSpec(
                Map.of("required_qualifications", List.of("학력·전공 무관")));

        for (int grade : List.of(1, 2, 3)) {
            List<Activity> filtered = GraduateOnlyActivityFilter.filterForGrade(
                    List.of(graduateOnly, open), grade);
            assertThat(filtered)
                    .as("grade=%d: 대졸 전용 키워드가 있는 활동은 제외되고 무관 활동은 남아야 한다", grade)
                    .containsExactly(open);
        }
    }

    @Test
    void grade가_4이상이면_필터하지_않는다() {
        Activity graduateOnly = activityWithTargetSpec(
                Map.of("required_qualifications", List.of("국내·외 학사 학위 이상 소지자")));

        List<Activity> filtered = GraduateOnlyActivityFilter.filterForGrade(List.of(graduateOnly), 4);

        assertThat(filtered)
                .as("졸업예정자(4학년 이상)는 대졸 공채에도 지원 가능하므로 필터하지 않는다")
                .containsExactly(graduateOnly);
    }

    @Test
    void grade가_null이면_필터하지_않는다() {
        Activity graduateOnly = activityWithTargetSpec(
                Map.of("required_qualifications", List.of("국내·외 학사 학위 이상 소지자")));

        List<Activity> filtered = GraduateOnlyActivityFilter.filterForGrade(List.of(graduateOnly), null);

        assertThat(filtered).containsExactly(graduateOnly);
    }

    @Test
    void 고등학교_졸업이나_학력무관_문구는_걸리지_않는다() {
        Activity highSchool = activityWithTargetSpec(
                Map.of("required_qualifications", List.of("고등학교 졸업이상")));
        Activity anyEducation = activityWithTargetSpec(
                Map.of("required_qualifications", List.of("학력무관")));
        Activity anyEducation2 = activityWithTargetSpec(
                Map.of("required_qualifications", List.of("학력·전공 무관")));

        List<Activity> filtered = GraduateOnlyActivityFilter.filterForGrade(
                List.of(highSchool, anyEducation, anyEducation2), 3);

        assertThat(filtered).containsExactly(highSchool, anyEducation, anyEducation2);
    }

    @Test
    void 대졸_대학졸업_기졸업자_키워드도_각각_제외_사유가_된다() {
        Activity daejol = activityWithTargetSpec(Map.of("required_qualifications", List.of("대졸 이상")));
        Activity daehakJoreop = activityWithTargetSpec(Map.of("required_qualifications", List.of("대학졸업자 우대")));
        Activity giJoreopja = activityWithTargetSpec(Map.of("required_qualifications", List.of("기졸업자만 지원 가능")));

        List<Activity> filtered = GraduateOnlyActivityFilter.filterForGrade(
                List.of(daejol, daehakJoreop, giJoreopja), 2);

        assertThat(filtered).isEmpty();
    }

    @Test
    void targetSpec이_없거나_비어있으면_제외하지_않는다() {
        Activity noSpec = Activity.builder().id(UUID.randomUUID()).type("EXTERNAL").name("스펙없음").build();
        Activity emptySpec = activityWithTargetSpec(Map.of());

        List<Activity> filtered = GraduateOnlyActivityFilter.filterForGrade(List.of(noSpec, emptySpec), 3);

        assertThat(filtered).containsExactly(noSpec, emptySpec);
    }

    @Test
    void 키워드가_required_qualifications가_아닌_다른_필드에_있어도_감지한다() {
        Activity nestedElsewhere = activityWithTargetSpec(
                Map.of("preferred_qualifications", List.of("국내외 대학졸업자 우대")));

        List<Activity> filtered = GraduateOnlyActivityFilter.filterForGrade(List.of(nestedElsewhere), 1);

        assertThat(filtered).isEmpty();
    }

    @Test
    void null이나_빈_목록은_그대로_반환한다() {
        assertThat(GraduateOnlyActivityFilter.filterForGrade(null, 1)).isNull();
        assertThat(GraduateOnlyActivityFilter.filterForGrade(List.of(), 1)).isEmpty();
    }
}
