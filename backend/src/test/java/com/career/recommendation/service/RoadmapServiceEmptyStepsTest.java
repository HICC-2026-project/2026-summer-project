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

    /**
     * priority 필드 하나만 빠져도(다른 필드는 전부 정상) 로드맵 전체가 버려지던 신규 회귀를
     * 고정한다. VALID_PRIORITIES.contains(t.getPriority())로 직접 검사했을 때,
     * Set.of(...).contains(null)이 false가 아니라 NullPointerException을 던지는 걸 놓쳐서
     * (Objects.requireNonNull 기반 구현) 이 NPE가 parseGeminiResponse 밖으로 튀어
     * callGeminiWithRetry의 catch(Exception)에 잡혔다 — 완전히 정상적인 응답이 "파싱 실패"로
     * 처리돼 2회 재시도(+2초 백오프, 최대 122초)를 다 태우고 폴백으로 떨어졌다. 옛 코드
     * (t.getPriority() != null ? ... : "MEDIUM")는 null-safe해서 이 문제가 없었는데, 화이트
     * 리스트 검증을 추가하면서 새로 생긴 회귀다.
     */
    @Test
    void priority_필드가_없어도_로드맵_전체가_버려지지_않는다() throws Exception {
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

        // priority만 빠지고 나머지(period·activity·reason)는 전부 정상인, 실제로 흔히
        // 발생 가능한 스키마 이탈 케이스.
        when(geminiService.generateRoadmap(any(), any(), any(), any(), any(), any()))
                .thenReturn("{\"timeline\":[{\"period\":\"3학년 2학기\",\"activity\":\"정보처리기사 취득\","
                        + "\"reason\":\"서류 가점\",\"activityIds\":[]}]}");

        ReflectionTestUtils.setField(roadmapService, "objectMapper", new ObjectMapper());

        RoadmapResponse response = roadmapService.getRoadmap(authentication);

        assertThat(response.isAiRoadmap())
                .as("priority 필드 누락은 스텝 하나를 MEDIUM으로 보정할 사유일 뿐, 응답 전체를 버릴 사유가 아니다")
                .isTrue();
        assertThat(response.getTimeline()).hasSize(1);
        assertThat(response.getTimeline().get(0).getActivity()).isEqualTo("정보처리기사 취득");
        assertThat(response.getTimeline().get(0).getPriority()).isEqualTo("MEDIUM");
    }

    @Test
    void priority_소문자도_정규화된다() throws Exception {
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

        when(geminiService.generateRoadmap(any(), any(), any(), any(), any(), any()))
                .thenReturn("{\"timeline\":[{\"period\":\"3학년 2학기\",\"priority\":\"high\","
                        + "\"activity\":\"정보처리기사 취득\",\"reason\":\"서류 가점\",\"activityIds\":[]}]}");

        ReflectionTestUtils.setField(roadmapService, "objectMapper", new ObjectMapper());

        RoadmapResponse response = roadmapService.getRoadmap(authentication);

        // 화이트리스트가 대소문자를 가리면 "high"가 조용히 MEDIUM으로 뭉개진다.
        assertThat(response.getTimeline().get(0).getPriority()).isEqualTo("HIGH");
    }
}
