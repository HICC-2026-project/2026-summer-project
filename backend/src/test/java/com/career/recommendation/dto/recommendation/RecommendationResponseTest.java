package com.career.recommendation.dto.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 샘플_비교_여부를_API_응답에_표시한다() throws Exception {
        RecommendationResponse response = RecommendationResponse.builder()
                .activities(List.of())
                .sampleComparisonData(true)
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.path("sampleComparisonData").asBoolean()).isTrue();
    }
}
