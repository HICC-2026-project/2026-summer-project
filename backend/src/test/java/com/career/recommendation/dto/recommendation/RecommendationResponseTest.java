package com.career.recommendation.dto.recommendation;

import com.career.recommendation.dto.position.SpecPositionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationResponseTest {

    // findAndRegisterModules()로 jackson-datatype-jsr310(LocalDate 등)을 등록한다.
    // Spring Boot의 자동구성 ObjectMapper(Jackson2ObjectMapperBuilder)가 실제로 이렇게
    // 동작하므로, 프로덕션과 다른 매퍼로 검증하면 여기서만 통과하고 실제론 깨지는(또는
    // 그 반대인) 거짓 신호가 나올 수 있다.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void 데모_데이터_포함_여부를_API_응답에_표시한다() throws Exception {
        RecommendationResponse response = RecommendationResponse.builder()
                .activities(List.of())
                .specPosition(SpecPositionResult.builder()
                        .basis("JOB")
                        .demoDataIncluded(true)
                        .build())
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.path("specPosition").path("demoDataIncluded").asBoolean()).isTrue();
    }

    /**
     * RecommendationService.deserialize()가 Recommendation.resultJson(캐시)을 읽을 때 쓰는
     * 것과 정확히 같은 직렬화→역직렬화 왕복을 검증한다.
     *
     * 이 클래스가 예전엔 직렬화(write)만 검증하고 역직렬화(read)는 전혀 검증하지 않아서,
     * 빌더 기반 역직렬화가 완전히 깨져 있는 버그(캐시를 읽으면 활동 없음·점수 0·플래그 전부
     * false인 빈 객체가 나오던 문제)를 놓쳤다. @Jacksonized가 빠지면 이 테스트가 실패한다 —
     * specPosition의 중첩 DTO(AxisPosition·SpecGap)까지 왕복을 검증하는 이유다.
     */
    @Test
    void 캐시_저장과_같은_방식으로_직렬화한_뒤_역직렬화하면_모든_필드가_복원된다() throws Exception {
        SpecPositionResult position = SpecPositionResult.builder()
                .basis("JOB")
                .basisMessage("BACKEND 합격자 12명의 분포와 비교한 결과입니다.")
                .sampleSize(12)
                .demoDataIncluded(false)
                .axes(List.of(SpecPositionResult.AxisPosition.builder()
                        .axis("GPA").label("학점")
                        .myValue("3.80/4.5").medianValue("3.70/4.5")
                        .percentile(62).coverage(11)
                        .build()))
                .gaps(List.of(SpecPositionResult.SpecGap.builder()
                        .name("정보처리기사").holderRatePercent(70)
                        .build()))
                .matchedCertifications(List.of("SQLD"))
                .unmatchedCertifications(List.of("완전정크자격증123"))
                .build();

        RecommendationResponse original = RecommendationResponse.builder()
                .activities(List.of(RecommendationResponse.ActivityRecommendation.builder()
                        .id(java.util.UUID.randomUUID())
                        .type("INTERNSHIP")
                        .name("테스트 활동")
                        .reason("테스트 이유")
                        .deadline(java.time.LocalDate.of(2026, 12, 31))
                        .build()))
                .specPosition(position)
                .aiRecommendation(true)
                .targetJobName("BACKEND")
                .scoreFormulaVersion(9)
                .build();

        String json = objectMapper.writeValueAsString(original);
        RecommendationResponse restored = objectMapper.readValue(json, RecommendationResponse.class);

        assertThat(restored.getActivities()).hasSize(1);
        assertThat(restored.getActivities().get(0).getName()).isEqualTo("테스트 활동");
        assertThat(restored.isAiRecommendation()).isTrue();
        assertThat(restored.getTargetJobName()).isEqualTo("BACKEND");
        assertThat(restored.getScoreFormulaVersion()).isEqualTo(9);

        SpecPositionResult restoredPosition = restored.getSpecPosition();
        assertThat(restoredPosition).isNotNull();
        assertThat(restoredPosition.getBasis()).isEqualTo("JOB");
        assertThat(restoredPosition.getSampleSize()).isEqualTo(12);
        assertThat(restoredPosition.getDemoDataIncluded()).isFalse();
        assertThat(restoredPosition.getAxes()).hasSize(1);
        assertThat(restoredPosition.getAxes().get(0).getPercentile()).isEqualTo(62);
        assertThat(restoredPosition.getAxes().get(0).getMyValue()).isEqualTo("3.80/4.5");
        assertThat(restoredPosition.getGaps()).hasSize(1);
        assertThat(restoredPosition.getGaps().get(0).getName()).isEqualTo("정보처리기사");
        assertThat(restoredPosition.getGaps().get(0).getHolderRatePercent()).isEqualTo(70);
        assertThat(restoredPosition.getMatchedCertifications()).containsExactly("SQLD");
        assertThat(restoredPosition.getUnmatchedCertifications()).containsExactly("완전정크자격증123");
    }
}
