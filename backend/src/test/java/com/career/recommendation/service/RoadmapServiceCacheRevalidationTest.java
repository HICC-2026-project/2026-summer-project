package com.career.recommendation.service;

import com.career.recommendation.dto.roadmap.RoadmapResponse;
import com.career.recommendation.dto.roadmap.RoadmapResponse.MatchedActivity;
import com.career.recommendation.dto.roadmap.RoadmapResponse.TimelineStep;
import com.career.recommendation.entity.Activity;
import com.career.recommendation.entity.RoadmapCache;
import com.career.recommendation.entity.User;
import com.career.recommendation.repository.ActivityRepository;
import com.career.recommendation.repository.RecommendationRepository;
import com.career.recommendation.repository.RoadmapCacheRepository;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserSpecRepository;
import com.career.recommendation.util.PromptDataBuilder;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 운영 제보 고정(RecommendationServiceCacheRevalidationTest의 로드맵 짝) — 캐시된 로드맵의
 * matchedActivities에 비활성화·마감·삭제된 활동이 남아 있으면 서빙 직전에 걸러낸다.
 * 스텝(period·activity 텍스트·reason) 자체는 유지하고, matchedActivities만 비워질 수 있다.
 */
@ExtendWith(MockitoExtension.class)
class RoadmapServiceCacheRevalidationTest {

    @Mock private CurrentUserService currentUserService;
    @Mock private UserSpecRepository userSpecRepository;
    @Mock private TargetJobRepository targetJobRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private SpecPositionService specPositionService;
    @Mock private RecommendationRepository recommendationRepository;
    @Mock private RoadmapCacheRepository roadmapCacheRepository;
    @Mock private RoadmapCacheService roadmapCacheService;
    @Mock private GeminiService geminiService;
    @Mock private PromptDataBuilder promptDataBuilder;
    @Mock private AiDailyAttemptLimiter aiDailyAttemptLimiter;
    @Mock private Authentication authentication;
    @Mock private User user;

    @InjectMocks private RoadmapService roadmapService;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private MatchedActivity matched(UUID id) {
        return MatchedActivity.builder().activityId(id).name("활동-" + id).type("EXTERNAL").build();
    }

    private Activity dbActivity(UUID id, boolean isActive, LocalDate deadline) {
        return Activity.builder().id(id).type("EXTERNAL").name("활동-" + id).isActive(isActive).deadline(deadline).build();
    }

    @Test
    void 캐시된_로드맵의_matchedActivities에서_비활성_마감_삭제된_활동이_제거되고_스텝은_유지된다() throws Exception {
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(targetJobRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        LocalDate today = LocalDate.now(KST);
        UUID keepId = UUID.randomUUID();       // 활성 + 상시(deadline null) → 유지
        UUID inactiveId = UUID.randomUUID();   // 관리자가 비활성화 → 제거
        UUID expiredId = UUID.randomUUID();    // 마감 지남 → 제거
        UUID deletedId = UUID.randomUUID();    // DB에서 삭제됨 → 제거

        RoadmapResponse cachedRoadmap = RoadmapResponse.builder()
                .aiRoadmap(true)
                .timeline(List.of(
                        TimelineStep.builder()
                                .period("1학년 1학기 (3~6월)").priority("HIGH")
                                .activity("정보처리기사 취득").reason("서류 가점")
                                .matchedActivities(List.of(matched(keepId), matched(inactiveId)))
                                .build(),
                        TimelineStep.builder()
                                .period("1학년 여름방학 (7~8월)").priority("MEDIUM")
                                .activity("SW 부트캠프 참가").reason("실무 역량")
                                .matchedActivities(List.of(matched(expiredId), matched(deletedId)))
                                .build(),
                        TimelineStep.builder()
                                .period("1학년 2학기 (9~11월)").priority("LOW")
                                .activity("오픈소스 기여").reason("협업 역량")
                                .matchedActivities(List.of())
                                .build()
                ))
                .build();

        ObjectMapper realMapper = new ObjectMapper().findAndRegisterModules();
        String json = realMapper.writeValueAsString(cachedRoadmap);
        RoadmapCache cached = RoadmapCache.builder()
                .id(UUID.randomUUID())
                .resultJson(json)
                .createdAt(LocalDateTime.now())
                .lastUpdatedDate(today)
                .build();
        when(roadmapCacheRepository.findByUser_Id(userId)).thenReturn(Optional.of(cached));

        // DB 대조: keepId만 살아있고 나머지는 비활성·마감·삭제 상태. deletedId는 findAllById 결과에서 빠진다.
        when(activityRepository.findAllById(List.of(keepId, inactiveId, expiredId, deletedId)))
                .thenReturn(List.of(
                        dbActivity(keepId, true, null),
                        dbActivity(inactiveId, false, null),
                        dbActivity(expiredId, true, today.minusDays(1))
                ));

        ReflectionTestUtils.setField(roadmapService, "objectMapper", realMapper);

        RoadmapResponse response = roadmapService.getRoadmap(authentication);

        assertThat(response.getTimeline()).hasSize(3);
        assertThat(response.getTimeline().get(0).getMatchedActivities())
                .extracting(MatchedActivity::getActivityId)
                .containsExactly(keepId);
        assertThat(response.getTimeline().get(1).getMatchedActivities())
                .as("마감 지난 활동과 삭제된 활동은 모두 제거되어 빈 배열이 되지만 스텝 자체는 남는다")
                .isEmpty();
        // 텍스트 내용은 필터와 무관하게 그대로 유지된다.
        assertThat(response.getTimeline().get(1).getActivity()).isEqualTo("SW 부트캠프 참가");
        assertThat(response.getTimeline().get(2).getMatchedActivities()).isEmpty();

        // 남은 활동(keepId)이 있어 캐시가 여전히 유효 판정되므로 Gemini를 다시 부르지 않는다.
        verify(geminiService, never()).generateRoadmap(any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * 실사용 확인: 캐시된 로드맵의 matchedActivities가 전부 마감·비활성화·삭제돼 필터 후 모든
     * 스텝에서 0건이 되면(원래는 매칭이 있었음) 스텝만 유지한 채 영원히 낡은 캐시를 주지 않고
     * 재생성 경로(하루 상한 통과 시 Gemini 재호출)로 들어가야 한다.
     */
    @Test
    void 매칭_활동이_전부_사라지면_재생성_경로로_진입한다() throws Exception {
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(targetJobRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(recommendationRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        LocalDate today = LocalDate.now(KST);
        UUID expiredId = UUID.randomUUID();

        RoadmapResponse cachedRoadmap = RoadmapResponse.builder()
                .aiRoadmap(true)
                .timeline(List.of(
                        TimelineStep.builder()
                                .period("1학년 1학기 (3~6월)").priority("HIGH")
                                .activity("정보처리기사 취득").reason("서류 가점")
                                .matchedActivities(List.of(matched(expiredId)))
                                .build()
                ))
                .build();

        ObjectMapper realMapper = new ObjectMapper().findAndRegisterModules();
        String json = realMapper.writeValueAsString(cachedRoadmap);
        RoadmapCache cached = RoadmapCache.builder()
                .id(UUID.randomUUID())
                .resultJson(json)
                .createdAt(LocalDateTime.now())
                .lastUpdatedDate(today)
                .build();
        when(roadmapCacheRepository.findByUser_Id(userId)).thenReturn(Optional.of(cached));

        // 유일한 매칭 활동(expiredId)이 마감 지나 필터 후 0건이 된다.
        when(activityRepository.findAllById(List.of(expiredId)))
                .thenReturn(List.of(dbActivity(expiredId, true, today.minusDays(1))));

        when(aiDailyAttemptLimiter.tryAcquire(eq(userId), any())).thenReturn(true);
        when(activityRepository.findRecommendableActivities(any(), any())).thenReturn(List.of());
        when(promptDataBuilder.serializeSpecForRoadmap(any())).thenReturn("{}");
        when(promptDataBuilder.buildTargetJobString(any())).thenReturn("미설정");
        when(promptDataBuilder.buildPositionContextText(any())).thenReturn("");
        when(promptDataBuilder.buildAvailableActivitiesJsonForRoadmap(any(), any())).thenReturn("[]");
        when(geminiService.generateRoadmap(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("{\"timeline\":[{\"period\":\"3학년 2학기\",\"priority\":\"HIGH\","
                        + "\"activity\":\"새로 생성된 활동\",\"reason\":\"새 사유\",\"activityIds\":[]}]}");

        ReflectionTestUtils.setField(roadmapService, "objectMapper", realMapper);

        RoadmapResponse response = roadmapService.getRoadmap(authentication);

        verify(geminiService).generateRoadmap(any(), any(), any(), any(), any(), any(), any());
        assertThat(response.getTimeline()).hasSize(1);
        assertThat(response.getTimeline().get(0).getActivity()).isEqualTo("새로 생성된 활동");
    }

    /**
     * 원래부터 matchedActivities가 하나도 없던 캐시(activity 텍스트 가이드만 있는 스텝)는
     * 필터를 거쳐도 항상 0건이므로, 이 사실만으로 재생성을 트리거하면 안 된다 — 불필요한
     * Gemini 재호출을 막는다.
     */
    @Test
    void 원래_매칭_활동이_없던_캐시는_재생성하지_않고_그대로_반환한다() throws Exception {
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(targetJobRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        LocalDate today = LocalDate.now(KST);
        RoadmapResponse cachedRoadmap = RoadmapResponse.builder()
                .aiRoadmap(true)
                .timeline(List.of(
                        TimelineStep.builder()
                                .period("1학년 1학기 (3~6월)").priority("HIGH")
                                .activity("자격증 취득 가이드").reason("가이드")
                                .matchedActivities(List.of())
                                .build()
                ))
                .build();

        ObjectMapper realMapper = new ObjectMapper().findAndRegisterModules();
        String json = realMapper.writeValueAsString(cachedRoadmap);
        RoadmapCache cached = RoadmapCache.builder()
                .id(UUID.randomUUID())
                .resultJson(json)
                .createdAt(LocalDateTime.now())
                .lastUpdatedDate(today)
                .build();
        when(roadmapCacheRepository.findByUser_Id(userId)).thenReturn(Optional.of(cached));

        ReflectionTestUtils.setField(roadmapService, "objectMapper", realMapper);

        RoadmapResponse response = roadmapService.getRoadmap(authentication);

        assertThat(response.getTimeline()).hasSize(1);
        assertThat(response.getTimeline().get(0).getActivity()).isEqualTo("자격증 취득 가이드");
        verify(geminiService, never()).generateRoadmap(any(), any(), any(), any(), any(), any(), any());
        // matchedActivities가 원래부터 비어 있어 필터 대상 id 자체가 없으므로 DB 대조도 생략된다.
        verify(activityRepository, never()).findAllById(any());
    }

    /**
     * 매칭 활동이 전부 사라져 재생성이 필요한 상황이라도, 하루 시도 상한(AiDailyAttemptLimiter)에
     * 막히면 Gemini를 다시 부르지 않고 "필터된(빈 matchedActivities) 캐시"를 dailyLimitReached
     * 플래그와 함께 그대로 반환해야 한다.
     */
    @Test
    void 하루_상한_초과시_필터된_캐시를_그대로_반환한다() throws Exception {
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(targetJobRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        LocalDate today = LocalDate.now(KST);
        UUID expiredId = UUID.randomUUID();

        RoadmapResponse cachedRoadmap = RoadmapResponse.builder()
                .aiRoadmap(true)
                .timeline(List.of(
                        TimelineStep.builder()
                                .period("1학년 1학기 (3~6월)").priority("HIGH")
                                .activity("정보처리기사 취득").reason("서류 가점")
                                .matchedActivities(List.of(matched(expiredId)))
                                .build()
                ))
                .build();

        ObjectMapper realMapper = new ObjectMapper().findAndRegisterModules();
        String json = realMapper.writeValueAsString(cachedRoadmap);
        RoadmapCache cached = RoadmapCache.builder()
                .id(UUID.randomUUID())
                .resultJson(json)
                .createdAt(LocalDateTime.now())
                .lastUpdatedDate(today)
                .build();
        when(roadmapCacheRepository.findByUser_Id(userId)).thenReturn(Optional.of(cached));

        when(activityRepository.findAllById(List.of(expiredId)))
                .thenReturn(List.of(dbActivity(expiredId, true, today.minusDays(1))));

        when(aiDailyAttemptLimiter.tryAcquire(eq(userId), any())).thenReturn(false);

        ReflectionTestUtils.setField(roadmapService, "objectMapper", realMapper);

        RoadmapResponse response = roadmapService.getRoadmap(authentication);

        assertThat(response.getDailyLimitReached()).isTrue();
        assertThat(response.getTimeline()).hasSize(1);
        assertThat(response.getTimeline().get(0).getMatchedActivities())
                .as("필터된(빈) matchedActivities라도 캐시를 그대로 반환한다")
                .isEmpty();
        verify(geminiService, never()).generateRoadmap(any(), any(), any(), any(), any(), any(), any());
    }
}
