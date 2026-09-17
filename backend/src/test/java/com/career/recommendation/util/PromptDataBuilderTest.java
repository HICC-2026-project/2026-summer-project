package com.career.recommendation.util;

import com.career.recommendation.dto.position.SpecPositionResult;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.UserSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptDataBuilderTest {

    private final PromptDataBuilder builder = new PromptDataBuilder(new ObjectMapper());

    @Test
    void 목표_직무가_없으면_미설정을_반환한다() {
        assertThat(builder.buildTargetJobString(null)).isEqualTo("미설정");
    }

    @Test
    void companySize_industry가_null이면_문자열_null_대신_미설정으로_대체된다() {
        // companySize·industry는 TargetJobRequest에 @NotBlank가 없는 선택 입력이라
        // null로 저장될 수 있다. null 폴백 없이 이어붙이면 "BACKEND / null / null"이라는
        // 리터럴 문자열이 그대로 Gemini 프롬프트에 들어가던 버그가 있었다.
        TargetJob targetJob = new TargetJob();
        targetJob.setJobType("BACKEND");
        targetJob.setCompanySize(null);
        targetJob.setIndustry(null);

        String result = builder.buildTargetJobString(targetJob);

        assertThat(result).isEqualTo("BACKEND / 미설정 / 미설정");
        assertThat(result).doesNotContain("null");
    }

    @Test
    void companySize_industry가_있으면_그대로_반영된다() {
        TargetJob targetJob = new TargetJob();
        targetJob.setJobType("BACKEND");
        targetJob.setCompanySize("대기업");
        targetJob.setIndustry("IT");

        assertThat(builder.buildTargetJobString(targetJob)).isEqualTo("BACKEND / 대기업 / IT");
    }

    @Test
    void 위치_갭_컨텍스트는_고정_형식이고_targetGap_허용_목록을_우선순위_순으로_닫아_준다() {
        // 스냅샷: 이 문자열이 Gemini 프롬프트에 그대로 들어간다. 형식이 바뀌면 추천·로드맵 프롬프트
        // 규칙(targetGap 이름 목록, 갭 우선순위)이 같이 깨지므로 의도된 변경인지 여기서 걸러낸다.
        SpecPositionResult position = SpecPositionResult.builder()
                .basis("JOB")
                .basisMessage("백엔드 합격자 12명의 분포와 비교한 결과입니다.")
                .axes(List.of(
                        axis("GPA", "학점", "3.80/4.5", "3.60/4.5", 72),
                        axis("LANGUAGE", "어학 성적", "미입력", "환산 850", null)))
                .gaps(List.of(
                        SpecPositionResult.SpecGap.builder().name("정보처리기사").holderRatePercent(70).build(),
                        SpecPositionResult.SpecGap.builder().name("SQLD").holderRatePercent(55).build()))
                .build();

        String text = builder.buildPositionContextText(position);

        assertThat(text).isEqualTo("""
                백엔드 합격자 12명의 분포와 비교한 결과입니다.
                - 학점: 내 값 3.80/4.5 / 합격자 중앙값 3.60/4.5 (합격자 분포에서 percentile 72)
                - 어학 성적: 내 값 미입력 / 합격자 중앙값 환산 850
                부족한 항목(갭 — 합격자 다수 보유, 사용자 미보유):
                - 정보처리기사 (합격자 70% 보유)
                - SQLD (합격자 55% 보유)
                targetGap에 쓸 수 있는 갭 이름(우선순위 순): 정보처리기사, SQLD, 어학 성적
                """);
    }

    @Test
    void 비교_데이터가_없으면_컨텍스트는_한_줄이다() {
        assertThat(builder.buildPositionContextText(null)).isEqualTo("합격자 비교 데이터 없음");
        assertThat(builder.buildPositionContextText(SpecPositionResult.builder().basis("NONE").build()))
                .isEqualTo("합격자 비교 데이터 없음");
    }

    @Test
    void 요구_영역_보유_미보유가_컨텍스트에_포함된다() {
        // E11-2 — 추천이 "미보유 영역을 채우는 활동"으로 좁혀지도록 보유/미보유를 명시한다.
        SpecPositionResult position = SpecPositionResult.builder()
                .basis("JOB")
                .basisMessage("백엔드 합격자 12명의 분포와 비교한 결과입니다.")
                .axes(List.of())
                .gaps(List.of())
                .areaCoverage(List.of(
                        areaCoverage("API", "API 개발", true),
                        areaCoverage("DB", "데이터베이스", false),
                        areaCoverage("AUTH", "인증", false)))
                .build();

        String text = builder.buildPositionContextText(position);

        assertThat(text).contains("목표 직무 요구 영역 — 보유: API 개발 / 미보유: 데이터베이스, 인증");
    }

    @Test
    void areaCoverage가_null이거나_비어있으면_요구_영역_줄이_생략된다() {
        SpecPositionResult noCoverage = SpecPositionResult.builder()
                .basis("JOB").basisMessage("백엔드 합격자 12명의 분포와 비교한 결과입니다.")
                .axes(List.of()).gaps(List.of()).areaCoverage(null)
                .build();
        SpecPositionResult emptyCoverage = SpecPositionResult.builder()
                .basis("JOB").basisMessage("백엔드 합격자 12명의 분포와 비교한 결과입니다.")
                .axes(List.of()).gaps(List.of()).areaCoverage(List.of())
                .build();

        assertThat(builder.buildPositionContextText(noCoverage)).doesNotContain("요구 영역");
        assertThat(builder.buildPositionContextText(emptyCoverage)).doesNotContain("요구 영역");
    }

    @Test
    void 경험의_role과_stack은_추천_로드맵_스펙_직렬화에_그대로_포함된다() {
        // (b) 각 경험의 role·stack을 프롬프트에 포함 — experiences 리스트를 그대로 직렬화하므로
        // 저장된 role·stack·areas가 있으면 자동으로 실린다. E11-1에서 이미 저장 형식이
        // 확정됐으므로 여기서는 "직렬화 경로가 값을 누락하지 않는다"만 고정한다.
        UserSpec userSpec = UserSpec.builder()
                .experiences(List.of(Map.of(
                        "type", "PROJECT",
                        "title", "결제 API 서버",
                        "role", "Spring 기반 결제 API 설계·구현",
                        "stack", List.of("Spring Boot", "PostgreSQL"),
                        "areas", List.of("API", "DB"))))
                .build();

        String recommendationJson = builder.serializeSpecForRecommendation(userSpec);
        String roadmapJson = builder.serializeSpecForRoadmap(userSpec);

        assertThat(recommendationJson).contains("Spring 기반 결제 API 설계·구현", "Spring Boot", "PostgreSQL");
        assertThat(roadmapJson).contains("Spring 기반 결제 API 설계·구현", "Spring Boot", "PostgreSQL");
    }

    private SpecPositionResult.AreaCoverage areaCoverage(String area, String label, boolean covered) {
        return SpecPositionResult.AreaCoverage.builder().area(area).label(label).covered(covered).build();
    }

    private SpecPositionResult.AxisPosition axis(String code, String label, String my, String median, Integer percentile) {
        return SpecPositionResult.AxisPosition.builder().axis(code).label(label)
                .myValue(my).medianValue(median).percentile(percentile).coverage(12).build();
    }
}
