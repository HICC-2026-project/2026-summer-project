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
                .areaCoverage(List.of(
                        SpecPositionResult.AreaCoverage.builder()
                                .area("API").label("API 개발").covered(true).build(),
                        SpecPositionResult.AreaCoverage.builder()
                                .area("AUTH").label("인증").covered(false).build()))
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
        assertThat(restoredPosition.getAreaCoverage()).hasSize(2);
        assertThat(restoredPosition.getAreaCoverage().get(0).getArea()).isEqualTo("API");
        assertThat(restoredPosition.getAreaCoverage().get(0).getLabel()).isEqualTo("API 개발");
        assertThat(restoredPosition.getAreaCoverage().get(0).isCovered()).isTrue();
        assertThat(restoredPosition.getAreaCoverage().get(1).isCovered()).isFalse();
    }

    /**
     * E11-2 — FE와 확정된 계약 형태 {"area":"API","label":"API 개발","covered":true}를
     * 그대로 고정한다. 목표 직무 미설정이면 areaCoverage 자체가 null이어야 한다(FE 계약).
     */
    @Test
    void areaCoverage는_FE_계약_형태로_직렬화되고_목표_직무_미설정이면_null이다() throws Exception {
        SpecPositionResult withCoverage = SpecPositionResult.builder()
                .basis("JOB")
                .areaCoverage(List.of(SpecPositionResult.AreaCoverage.builder()
                        .area("API").label("API 개발").covered(true).build()))
                .build();
        SpecPositionResult withoutTargetJob = SpecPositionResult.builder()
                .basis("OVERALL")
                .areaCoverage(null)
                .build();

        JsonNode covered = objectMapper.readTree(objectMapper.writeValueAsString(withCoverage));
        JsonNode uncovered = objectMapper.readTree(objectMapper.writeValueAsString(withoutTargetJob));

        JsonNode firstArea = covered.path("areaCoverage").get(0);
        assertThat(firstArea.path("area").asText()).isEqualTo("API");
        assertThat(firstArea.path("label").asText()).isEqualTo("API 개발");
        assertThat(firstArea.path("covered").asBoolean()).isTrue();
        assertThat(uncovered.path("areaCoverage").isNull()).isTrue();
    }

    /**
     * E11-2(2차) — coverageSource/coverageSampleSize(SpecPositionResult)와 passerRatio
     * (AreaCoverage)는 이번에 새로 추가된 필드다. v9(CURRENT_SCORE_FORMULA_VERSION 도입 전)
     * 캐시 JSON에는 이 필드들이 아예 없는데, RecommendationService.deserialize()가 이런 옛
     * Recommendation.resultJson을 읽을 때 예외 없이 역직렬화되고(스프링 기본 ObjectMapper는
     * FAIL_ON_UNKNOWN_PROPERTIES가 꺼져 있고, 반대로 이 응답엔 없던 필드가 늘어난 경우라
     * 단순히 없는 필드는 null이 된다) 새 필드는 그냥 null로 채워져야 한다 — 캐시를 지우지
     * 않고 배포해도 기존 사용자 캐시를 읽다가 깨지면 안 된다.
     */
    @Test
    void 신규_필드가_없는_구버전_캐시_JSON도_예외_없이_역직렬화되고_새_필드는_null이다() throws Exception {
        String legacyJson = """
                {
                  "activities": [],
                  "aiRecommendation": true,
                  "targetJobName": "BACKEND",
                  "scoreFormulaVersion": 9,
                  "specPosition": {
                    "basis": "JOB",
                    "basisMessage": "백엔드 합격자 12명의 분포와 비교한 결과입니다.",
                    "sampleSize": 12,
                    "areaCoverage": [
                      {"area": "API", "label": "API 개발", "covered": true}
                    ]
                  }
                }
                """;

        RecommendationResponse restored = objectMapper.readValue(legacyJson, RecommendationResponse.class);

        assertThat(restored).isNotNull();
        assertThat(restored.getScoreFormulaVersion()).isEqualTo(9);
        SpecPositionResult position = restored.getSpecPosition();
        assertThat(position).isNotNull();
        assertThat(position.getAreaCoverage()).hasSize(1);
        // 새로 추가된 필드는 JSON에 없었으므로 전부 null — 이 값을 보고
        // RecommendationService가 scoreFormulaVersion으로 legacy 여부를 판정한다(coverageSource
        // 자체를 legacy 판정에 쓰지 않는 이유: 목표 직무 미설정이면 원래도 null이라 구분이 안 된다).
        assertThat(position.getCoverageSource()).isNull();
        assertThat(position.getCoverageSampleSize()).isNull();
        assertThat(position.getAreaCoverage().get(0).getPasserRatio()).isNull();
    }
}
