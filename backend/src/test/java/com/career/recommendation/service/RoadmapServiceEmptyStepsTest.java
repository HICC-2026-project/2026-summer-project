package com.career.recommendation.service;

import com.career.recommendation.dto.roadmap.RoadmapResponse;
import com.career.recommendation.entity.User;
import com.career.recommendation.repository.ActivityRepository;
import com.career.recommendation.repository.RecommendationRepository;
import com.career.recommendation.repository.RoadmapCacheRepository;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserSpecRepository;
import com.career.recommendation.util.PromptDataBuilder;
import com.career.recommendation.util.SimilarSpecFinder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 내용이 텅 빈 로드맵 스텝({"timeline":[{},{},{}]} 같은 형태만 있고 activity·matchedActivities가
 * 전부 빈 응답)이 "AI 성공"으로 판정돼 캐시에 영구 저장되던 버그를 고정한다.
 *
 * 예전엔 steps.isEmpty()로만 성공 여부를 판정해서, 알맹이 없는 스텝이라도 개수만 있으면
 * aiRoadmap=true로 통과했다. 이렇게 저장된 캐시는 스스로 회복되지 않는다 —
 * hasUsableCachedActivities()가 matchedActivities를 flatMap해서 마감일을 보는데 전부 비어있으니
 * noneMatch가 항상 true가 되어 "사용 가능"으로 판정되고, 유저가 스펙을 다시 저장하기 전까지
 * 빈 로드맵이 영구히 반환된다.
 */
@ExtendWith(MockitoExtension.class)
class RoadmapServiceEmptyStepsTest {

    @Mock private CurrentUserService currentUserService;
    @Mock private UserSpecRepository userSpecRepository;
    @Mock private TargetJobRepository targetJobRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private SimilarSpecFinder similarSpecFinder;
    @Mock private RecommendationService recommendationService;
    @Mock private RecommendationRepository recommendationRepository;
    @Mock private RoadmapCacheRepository roadmapCacheRepository;
    @Mock private RoadmapCacheService roadmapCacheService;
    @Mock private GeminiService geminiService;
    @Mock private PromptDataBuilder promptDataBuilder;
    @Mock private Authentication authentication;
    @Mock private User user;

    @InjectMocks private RoadmapService roadmapService;

    @Test
    void 알맹이_없는_스텝만_있으면_AI_성공으로_처리하지_않고_폴백을_쓴다() throws Exception {
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);

        when(roadmapCacheRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(targetJobRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(recommendationRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(similarSpecFinder.find(any(), any(), any())).thenReturn(List.of());
        when(activityRepository.findRecommendableActivities(any(), any())).thenReturn(List.of());

        when(promptDataBuilder.serializeSpecForRoadmap(any())).thenReturn("{}");
        when(promptDataBuilder.buildTargetJobString(any())).thenReturn("미설정");
        when(promptDataBuilder.buildSimilarCasesText(any())).thenReturn("");
        when(promptDataBuilder.buildAvailableActivitiesJson(any())).thenReturn("[]");

        // 형태만 있고 알맹이는 없는 응답 — period/priority/activity/reason/activityIds 전부 없음.
        when(geminiService.generateRoadmap(any(), any(), any(), any(), any(), any()))
                .thenReturn("{\"timeline\":[{},{},{}]}");

        ReflectionTestUtils.setField(roadmapService, "objectMapper", new ObjectMapper());

        RoadmapResponse response = roadmapService.getRoadmap(authentication);

        assertThat(response.isAiRoadmap())
                .as("알맹이 없는 스텝만 온 응답은 AI 성공으로 취급하면 안 된다")
                .isFalse();
        // 폴백 로드맵은 실제 내용(activity·reason 텍스트)이 채워져 있다.
        assertThat(response.getTimeline()).isNotEmpty();
        assertThat(response.getTimeline().get(0).getActivity()).isNotBlank();
        // AI 실패(폴백)로 판정됐으니 캐시에 저장되면 안 된다 — 저장되면 다음 요청부터
        // 영구히 이 빈 상태가 재현될 위험이 있다.
        verify(roadmapCacheService, never()).save(any(), any());
    }
}
