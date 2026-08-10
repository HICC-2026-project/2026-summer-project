package com.career.recommendation.dto.roadmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RoadmapService.getRoadmap()이 RoadmapCache.resultJson(캐시)을 읽을 때 쓰는 것과
 * 정확히 같은 직렬화→역직렬화 왕복을 검증한다.
 *
 * isAiRoadmap 필드가 "is" 접두사 때문에 Jackson 직렬화 키("aiRoadmap")와 암묵적
 * all-args 생성자 파라미터명("isAiRoadmap")이 어긋나, 캐시를 역직렬화할 때마다 조용히
 * false로 떨어지던 버그가 있었다(RecommendationResponse.isAiRecommendation과 같은
 * 근본 원인). @Jacksonized 도입 전 필드명(isAiRoadmap)으로 되돌리면 이 테스트가
 * 실패한다 — 회귀를 실제로 잡는지 그렇게 확인했다.
 */
class RoadmapResponseTest {

    // findAndRegisterModules()로 jackson-datatype-jsr310(LocalDate 등)을 등록한다.
    // Spring Boot의 자동구성 ObjectMapper와 동일하게 맞춰야 프로덕션과 다른 매퍼로
    // 검증해 생기는 거짓 신호를 피할 수 있다.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void 캐시_저장과_같은_방식으로_직렬화한_뒤_역직렬화하면_모든_필드가_복원된다() throws Exception {
        RoadmapResponse original = RoadmapResponse.builder()
                .timeline(List.of(RoadmapResponse.TimelineStep.builder()
                        .period("3학년 2학기")
                        .priority("HIGH")
                        .activity("SW 교육")
                        .reason("핵심 시기")
                        .matchedActivities(List.of(RoadmapResponse.MatchedActivity.builder()
                                .activityId(UUID.randomUUID())
                                .name("테스트 활동")
                                .type("EDUCATION")
                                .organization("테스트 기관")
                                .deadline(LocalDate.of(2026, 12, 31))
                                .url("https://example.com")
                                .build()))
                        .build()))
                .aiRoadmap(true)
                .build();

        String json = objectMapper.writeValueAsString(original);
        RoadmapResponse restored = objectMapper.readValue(json, RoadmapResponse.class);

        assertThat(restored.isAiRoadmap()).isTrue();
        assertThat(restored.getTimeline()).hasSize(1);
        RoadmapResponse.TimelineStep step = restored.getTimeline().get(0);
        assertThat(step.getPeriod()).isEqualTo("3학년 2학기");
        assertThat(step.getMatchedActivities()).hasSize(1);
        assertThat(step.getMatchedActivities().get(0).getName()).isEqualTo("테스트 활동");
    }
}
