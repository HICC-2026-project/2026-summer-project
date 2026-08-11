package com.career.recommendation.dto.recommendation;

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
    void 샘플_비교_여부를_API_응답에_표시한다() throws Exception {
        RecommendationResponse response = RecommendationResponse.builder()
                .activities(List.of())
                .sampleComparisonData(true)
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.path("sampleComparisonData").asBoolean()).isTrue();
    }

    /**
     * RecommendationService.deserialize()가 Recommendation.resultJson(캐시)을 읽을 때 쓰는
     * 것과 정확히 같은 직렬화→역직렬화 왕복을 검증한다.
     *
     * 이 클래스가 예전엔 직렬화(write)만 검증하고 역직렬화(read)는 전혀 검증하지 않아서,
     * 빌더 기반 역직렬화가 완전히 깨져 있는 버그(캐시를 읽으면 활동 없음·점수 0·플래그 전부
     * false인 빈 객체가 나오던 문제)를 놓쳤다. @Jacksonized 도입 전 코드로 되돌리면 이
     * 테스트가 실패한다 — 회귀를 실제로 잡는지 그렇게 확인했다.
     */
    @Test
    void 캐시_저장과_같은_방식으로_직렬화한_뒤_역직렬화하면_모든_필드가_복원된다() throws Exception {
        RecommendationResponse original = RecommendationResponse.builder()
                .activities(List.of(RecommendationResponse.ActivityRecommendation.builder()
                        .id(java.util.UUID.randomUUID())
                        .type("INTERNSHIP")
                        .name("테스트 활동")
                        .reason("테스트 이유")
                        .deadline(java.time.LocalDate.of(2026, 12, 31))
                        .build()))
                .matchScore(77)
                .comparisonMessage("유사 BACKEND 합격자 5명과 비교한 결과입니다.")
                .aiRecommendation(true)
                .targetJobName("BACKEND")
                .similarPasserCount(5)
                .compareRows(List.of())
                .sampleComparisonData(false)
                .unrecognizedCertifications(List.of("완전정크자격증123"))
                .scoreFormulaVersion(6)
                .build();

        String json = objectMapper.writeValueAsString(original);
        RecommendationResponse restored = objectMapper.readValue(json, RecommendationResponse.class);

        assertThat(restored.getActivities()).hasSize(1);
        assertThat(restored.getActivities().get(0).getName()).isEqualTo("테스트 활동");
        assertThat(restored.getMatchScore()).isEqualTo(77);
        assertThat(restored.isAiRecommendation()).isTrue();
        assertThat(restored.getTargetJobName()).isEqualTo("BACKEND");
        assertThat(restored.getSimilarPasserCount()).isEqualTo(5);
        assertThat(restored.getSampleComparisonData()).isFalse();
        assertThat(restored.getUnrecognizedCertifications()).containsExactly("완전정크자격증123");
        assertThat(restored.getScoreFormulaVersion()).isEqualTo(6);
    }
}
