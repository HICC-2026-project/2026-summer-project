package com.career.recommendation.service;

import com.career.recommendation.dto.position.SpecPositionResult;
import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.dto.recommendation.RecommendationResponse.ActivityRecommendation;
import com.career.recommendation.entity.Activity;
import com.career.recommendation.entity.Recommendation;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 운영 제보 고정: 추천 캐시(recommendations.result_json)는 스펙이 바뀔 때만 재생성되는데,
 * 캐시 생성 후 활동이 마감되거나(스케줄러가 is_active=false 처리) 관리자가 비활성화하면
 * 캐시가 그 활동을 계속 노출했다("마감 끝난 활동이 추천에 뜬다" 제보, 2026-09-17).
 *
 * GET /recommendations가 캐시를 반환하기 직전 캐시 속 활동을 DB와 대조해 비활성·마감·삭제된
 * 항목을 제거하는 RecommendationService.filterStaleActivities를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceCacheRevalidationTest {

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

    private UUID setUpUser() {
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        // 스펙 변경 판정이 결과를 흔들지 않도록 스펙·목표직무는 항상 미설정으로 둔다.
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(targetJobRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(recommendationService, "objectMapper", new ObjectMapper().findAndRegisterModules());
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

    /** 최신 스키마(legacy 아님) 캐시 JSON을 만든다. */
    private Recommendation cachedRecommendation(UUID userId, List<ActivityRecommendation> activities) {
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

    private Activity dbActivity(UUID id, boolean isActive, LocalDate deadline) {
        return Activity.builder()
                .id(id).type("EXTERNAL").name("활동-" + id)
                .isActive(isActive).deadline(deadline)
                .build();
    }

    private ActivityRecommendation cachedActivity(UUID id, LocalDate deadline) {
        return ActivityRecommendation.builder()
                .id(id).type("EXTERNAL").name("활동-" + id).reason("이유").deadline(deadline)
                .build();
    }

    @Test
    void 캐시에_비활성_활동이_있으면_응답에서_제거된다() {
        UUID userId = setUpUser();
        UUID activeId = UUID.randomUUID();
        UUID inactiveId = UUID.randomUUID();

        Recommendation cached = cachedRecommendation(userId,
                List.of(cachedActivity(activeId, null), cachedActivity(inactiveId, null)));
        when(recommendationRepository.findByUser_Id(userId)).thenReturn(Optional.of(cached));
        when(activityRepository.findAllById(List.of(activeId, inactiveId)))
                .thenReturn(List.of(dbActivity(activeId, true, null), dbActivity(inactiveId, false, null)));

        RecommendationResponse response = recommendationService.getRecommendations(authentication);

        assertThat(response.getActivities()).extracting(ActivityRecommendation::getId)
                .containsExactly(activeId);
        // 남은 활동이 있어 캐시가 여전히 유효하므로 Gemini를 다시 부르지 않는다.
        verify(geminiService, never()).generateRecommendation(any(), any(), any(), any(), any());
    }

    @Test
    void 마감_지난_활동은_제거되고_상시_모집_null_deadline은_유지된다() {
        UUID userId = setUpUser();
        UUID expiredId = UUID.randomUUID();
        UUID alwaysOpenId = UUID.randomUUID();
        LocalDate today = LocalDate.now(KST);

        Recommendation cached = cachedRecommendation(userId,
                List.of(cachedActivity(expiredId, today.minusDays(1)), cachedActivity(alwaysOpenId, null)));
        when(recommendationRepository.findByUser_Id(userId)).thenReturn(Optional.of(cached));
        // DB 마감일은 캐시 생성 시점 값과 다를 수 있으므로 DB 값을 기준으로 판단한다.
        when(activityRepository.findAllById(List.of(expiredId, alwaysOpenId)))
                .thenReturn(List.of(
                        dbActivity(expiredId, true, today.minusDays(1)),
                        dbActivity(alwaysOpenId, true, null)));

        RecommendationResponse response = recommendationService.getRecommendations(authentication);

        assertThat(response.getActivities()).extracting(ActivityRecommendation::getId)
                .containsExactly(alwaysOpenId);
        verify(geminiService, never()).generateRecommendation(any(), any(), any(), any(), any());
    }

    @Test
    void 필터로_전부_제거되면_상한_내에서_재생성을_시도한다() throws Exception {
        UUID userId = setUpUser();
        UUID deletedId = UUID.randomUUID();

        Recommendation cached = cachedRecommendation(userId, List.of(cachedActivity(deletedId, null)));
        when(recommendationRepository.findByUser_Id(userId)).thenReturn(Optional.of(cached));
        // DB에서 완전히 사라진 활동(관리자 삭제 등) — findAllById가 빈 목록을 반환.
        when(activityRepository.findAllById(List.of(deletedId))).thenReturn(List.of());

        when(aiDailyAttemptLimiter.tryAcquire(userId, AiDailyAttemptLimiter.KIND_RECOMMENDATION)).thenReturn(true);
        when(specPositionService.calculate(any(), any())).thenReturn(validPosition());

        UUID freshId = UUID.randomUUID();
        Activity fresh = dbActivity(freshId, true, LocalDate.now(KST).plusDays(10));
        when(activityRepository.findRecommendableActivities(any(), any())).thenReturn(List.of(fresh));
        when(promptDataBuilder.buildAvailableActivitiesJson(any())).thenReturn("[]");
        when(promptDataBuilder.serializeSpecForRecommendation(any())).thenReturn("{}");
        when(promptDataBuilder.buildTargetJobString(any())).thenReturn("미설정");
        when(promptDataBuilder.buildPositionContextText(any())).thenReturn("");
        when(geminiService.generateRecommendation(any(), any(), any(), any(), any()))
                .thenReturn("{\"activities\":[{\"id\":\"" + freshId + "\",\"name\":\"새 활동\",\"reason\":\"새로 생성\"}]}");

        RecommendationResponse response = recommendationService.getRecommendations(authentication);

        assertThat(response.isAiRecommendation()).isTrue();
        assertThat(response.getActivities()).extracting(ActivityRecommendation::getId).containsExactly(freshId);
        verify(recommendationCacheService, times(1)).save(eq(user), any());
    }

    @Test
    void 필터로_전부_제거됐지만_상한_초과면_빈_목록으로_정상_응답한다() {
        UUID userId = setUpUser();
        UUID deletedId = UUID.randomUUID();

        Recommendation cached = cachedRecommendation(userId, List.of(cachedActivity(deletedId, null)));
        when(recommendationRepository.findByUser_Id(userId)).thenReturn(Optional.of(cached));
        when(activityRepository.findAllById(List.of(deletedId))).thenReturn(List.of());

        // 하루 시도 상한 초과 — Gemini를 다시 부르지 않는다.
        when(aiDailyAttemptLimiter.tryAcquire(userId, AiDailyAttemptLimiter.KIND_RECOMMENDATION)).thenReturn(false);
        when(specPositionService.calculate(any(), any())).thenReturn(validPosition());

        RecommendationResponse response = recommendationService.getRecommendations(authentication);

        assertThat(response).isNotNull();
        assertThat(response.getActivities()).isEmpty();
        assertThat(response.getDailyLimitReached()).isTrue();
        verify(geminiService, never()).generateRecommendation(any(), any(), any(), any(), any());
    }
}
