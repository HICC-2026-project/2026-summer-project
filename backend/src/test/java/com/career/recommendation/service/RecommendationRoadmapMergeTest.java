package com.career.recommendation.service;

import com.career.recommendation.dto.position.SpecPositionResult;
import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.dto.recommendation.RecommendationResponse.ActivityRecommendation;
import com.career.recommendation.dto.roadmap.RoadmapResponse;
import com.career.recommendation.dto.roadmap.RoadmapResponse.MatchedActivity;
import com.career.recommendation.dto.roadmap.RoadmapResponse.TimelineStep;
import com.career.recommendation.entity.Activity;
import com.career.recommendation.entity.Recommendation;
import com.career.recommendation.entity.RoadmapCache;
import com.career.recommendation.entity.User;
import com.career.recommendation.repository.ActivityRepository;
import com.career.recommendation.repository.RecommendationRepository;
import com.career.recommendation.repository.RoadmapCacheRepository;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserSpecRepository;
import com.career.recommendation.util.PromptDataBuilder;
import com.career.recommendation.util.SpecPositionCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실사용 피드백 고정(2026-09-17): "추천이랑 로드맵이 띄워주는 게 달라. 둘 다 괜찮은 제안인데,
 * 적어도 홈 화면엔 로드맵에 떠 있는 건 모두 떴으면 좋겠어."
 *
 * 로드맵 생성 시 이미 추천된 활동을 프롬프트에서 제외(excludeIds)하기 때문에 두 목록이
 * 의도적으로 갈라지는데, RecommendationService.mergeRoadmapActivities가 홈 추천 응답을
 * 반환하기 직전 로드맵 캐시의 matchedActivities 중 추천 목록에 없는 것을 뒤에 덧붙인다.
 * 병합은 읽기 시점에만 일어나고 추천 캐시에 다시 저장되지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationRoadmapMergeTest {

    @Mock private CurrentUserService currentUserService;
    @Mock private UserSpecRepository userSpecRepository;
    @Mock private TargetJobRepository targetJobRepository;
    @Mock private RecommendationRepository recommendationRepository;
    @Mock private RecommendationCacheService recommendationCacheService;
    @Mock private RoadmapCacheRepository roadmapCacheRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private SpecPositionService specPositionService;
    @Mock private GeminiService geminiService;
    @Mock private PromptDataBuilder promptDataBuilder;
    @Mock private AiDailyAttemptLimiter aiDailyAttemptLimiter;
    @Mock private Authentication authentication;
    @Mock private User user;

    @InjectMocks private RecommendationService recommendationService;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final Map<UUID, Activity> dbActivitiesById = new HashMap<>();

    private UUID setUpUser() {
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(targetJobRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(recommendationService, "objectMapper", new ObjectMapper().findAndRegisterModules());
        // 여러 findAllById 호출(추천 캐시 재검증용 · 로드맵 병합용)을 각각의 인자에 맞춰 응답한다.
        when(activityRepository.findAllById(any())).thenAnswer(invocation -> {
            List<UUID> ids = invocation.getArgument(0);
            return ids.stream().map(dbActivitiesById::get).filter(Objects::nonNull).toList();
        });
        return userId;
    }

    private SpecPositionResult validPosition() {
        return SpecPositionResult.builder()
                .basis("NONE")
                .basisMessage("아직 비교할 합격자 데이터가 부족합니다.")
                .sampleSize(0)
                .axes(List.of())
                .gaps(List.of())
                .build();
    }

    private Recommendation cachedRecommendation(List<ActivityRecommendation> activities) {
        RecommendationResponse cachedResponse = RecommendationResponse.builder()
                .activities(activities)
                .specPosition(validPosition())
                .targetJobName("미설정")
                .aiRecommendation(true)
                .scoreFormulaVersion(SpecPositionCalculator.CURRENT_SCORE_FORMULA_VERSION)
                .build();
        String json;
        try {
            json = new ObjectMapper().findAndRegisterModules().writeValueAsString(cachedResponse);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Recommendation.builder()
                .id(UUID.randomUUID())
                .resultJson(json)
                .createdAt(LocalDateTime.now())
                .lastUpdatedDate(LocalDate.now(KST))
                .build();
    }

    private RoadmapCache cachedRoadmap(List<TimelineStep> timeline) {
        RoadmapResponse roadmap = RoadmapResponse.builder().aiRoadmap(true).timeline(timeline).build();
        String json;
        try {
            json = new ObjectMapper().findAndRegisterModules().writeValueAsString(roadmap);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return RoadmapCache.builder()
                .id(UUID.randomUUID())
                .resultJson(json)
                .createdAt(LocalDateTime.now())
                .lastUpdatedDate(LocalDate.now(KST))
                .build();
    }

    private Activity dbActivity(UUID id, boolean isActive, LocalDate deadline) {
        Activity activity = Activity.builder()
                .id(id).type("EXTERNAL").name("활동-" + id)
                .isActive(isActive).deadline(deadline)
                .build();
        dbActivitiesById.put(id, activity);
        return activity;
    }

    private ActivityRecommendation cachedActivity(UUID id, LocalDate deadline) {
        return ActivityRecommendation.builder()
                .id(id).type("EXTERNAL").name("활동-" + id).reason("이유").deadline(deadline)
                .build();
    }

    private MatchedActivity matched(UUID id) {
        return MatchedActivity.builder().activityId(id).name("활동-" + id).type("EXTERNAL").build();
    }

    @Test
    void 로드맵_캐시에만_있는_활동이_추천_목록_뒤에_붙는다() {
        UUID userId = setUpUser();
        UUID existingId = UUID.randomUUID();
        UUID roadmapOnlyId = UUID.randomUUID();

        when(recommendationRepository.findByUser_Id(userId))
                .thenReturn(Optional.of(cachedRecommendation(List.of(cachedActivity(existingId, null)))));
        when(roadmapCacheRepository.findByUser_Id(userId)).thenReturn(Optional.of(cachedRoadmap(List.of(
                TimelineStep.builder()
                        .period("3학년 2학기").priority("HIGH")
                        .activity("SW 아카데미 지원").reason("서류 가점을 위한 핵심 활동")
                        .matchedActivities(List.of(matched(roadmapOnlyId)))
                        .build()
        ))));

        dbActivity(existingId, true, null);
        dbActivity(roadmapOnlyId, true, LocalDate.now(KST).plusDays(30));

        RecommendationResponse response = recommendationService.getRecommendations(authentication);

        assertThat(response.getActivities()).extracting(ActivityRecommendation::getId)
                .containsExactly(existingId, roadmapOnlyId);
        ActivityRecommendation appended = response.getActivities().get(1);
        assertThat(appended.getReason()).contains("로드맵 1번째 시기 활동").contains("서류 가점을 위한 핵심 활동");
        assertThat(appended.getType()).isEqualTo("EXTERNAL");
        assertThat(appended.getTargetGap()).isNull();
        // Gemini를 새로 호출하지 않는다 — 병합은 순수 읽기 시점 표시용이다.
        verify(geminiService, never()).generateRecommendation(any(), any(), any(), any(), any());
    }

    @Test
    void 추천_목록에_이미_있는_활동은_로드맵에서_다시_붙지_않는다() {
        UUID userId = setUpUser();
        UUID sharedId = UUID.randomUUID();

        when(recommendationRepository.findByUser_Id(userId))
                .thenReturn(Optional.of(cachedRecommendation(List.of(cachedActivity(sharedId, null)))));
        when(roadmapCacheRepository.findByUser_Id(userId)).thenReturn(Optional.of(cachedRoadmap(List.of(
                TimelineStep.builder()
                        .period("3학년 2학기").priority("HIGH")
                        .activity("이미 추천된 활동").reason("이미 추천된 활동")
                        .matchedActivities(List.of(matched(sharedId)))
                        .build()
        ))));

        dbActivity(sharedId, true, null);

        RecommendationResponse response = recommendationService.getRecommendations(authentication);

        assertThat(response.getActivities()).extracting(ActivityRecommendation::getId)
                .containsExactly(sharedId);
    }

    @Test
    void 마감_지난_로드맵_매칭_활동은_붙지_않는다() {
        UUID userId = setUpUser();
        UUID existingId = UUID.randomUUID();
        UUID expiredRoadmapId = UUID.randomUUID();
        LocalDate today = LocalDate.now(KST);

        when(recommendationRepository.findByUser_Id(userId))
                .thenReturn(Optional.of(cachedRecommendation(List.of(cachedActivity(existingId, null)))));
        when(roadmapCacheRepository.findByUser_Id(userId)).thenReturn(Optional.of(cachedRoadmap(List.of(
                TimelineStep.builder()
                        .period("3학년 2학기").priority("HIGH")
                        .activity("마감 지난 활동").reason("마감 지난 활동")
                        .matchedActivities(List.of(matched(expiredRoadmapId)))
                        .build()
        ))));

        dbActivity(existingId, true, null);
        dbActivity(expiredRoadmapId, true, today.minusDays(1)); // 마감 지남

        RecommendationResponse response = recommendationService.getRecommendations(authentication);

        assertThat(response.getActivities()).extracting(ActivityRecommendation::getId)
                .containsExactly(existingId);
    }

    @Test
    void 로드맵_캐시가_없으면_추천_목록이_그대로_반환된다() {
        UUID userId = setUpUser();
        UUID existingId = UUID.randomUUID();

        when(recommendationRepository.findByUser_Id(userId))
                .thenReturn(Optional.of(cachedRecommendation(List.of(cachedActivity(existingId, null)))));
        when(roadmapCacheRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        dbActivity(existingId, true, null);

        RecommendationResponse response = recommendationService.getRecommendations(authentication);

        assertThat(response.getActivities()).extracting(ActivityRecommendation::getId)
                .containsExactly(existingId);
    }
}
