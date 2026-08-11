package com.career.recommendation.service;

import com.career.recommendation.dto.roadmap.RoadmapResponse;
import com.career.recommendation.entity.Activity;
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

    /**
     * Gemini 폴백 로드맵의 시기 라벨이 요청 시점(today)과 무관하게 "N학년 2학기 (9~11월)"부터
     * 하드코딩돼 있던 회귀를 고정한다. 4월에 3학년이 폴백을 받으면 예전 코드는 1번째(HIGH)
     * 시기로 ~5개월 뒤인 "3학년 2학기"를 보여줬다 — "6개월 로드맵"이라는 계약을 벗어난다.
     * computeFallbackPeriods는 private이라 리플렉션으로 직접 호출해, 시스템 시계와 무관하게
     * 결정적으로 검증한다.
     */
    @Test
    void 폴백_로드맵_시기_라벨은_실제_현재월부터_순서대로_계산된다() {
        // 4월(1학기 구간) — 예전 코드라면 여기서도 "3학년 2학기"가 나왔을 것.
        String[] april = ReflectionTestUtils.invokeMethod(
                roadmapService, "computeFallbackPeriods", 3, java.time.LocalDate.of(2026, 4, 15));
        assertThat(april).containsExactly(
                "3학년 1학기 (3~6월)", "3학년 여름방학 (7~8월)", "3학년 2학기 (9~11월)");

        // 12월(겨울방학 구간) — 다음 시기부터는 학년이 하나 올라가야 한다.
        String[] december = ReflectionTestUtils.invokeMethod(
                roadmapService, "computeFallbackPeriods", 3, java.time.LocalDate.of(2026, 12, 1));
        assertThat(december).containsExactly(
                "3학년 겨울방학 (12~2월)", "4학년 1학기 (3~6월)", "4학년 여름방학 (7~8월)");

        // 4학년 겨울방학 다음은 "졸업 후 취업 준비"로 고정돼야 한다.
        String[] graduating = ReflectionTestUtils.invokeMethod(
                roadmapService, "computeFallbackPeriods", 4, java.time.LocalDate.of(2026, 12, 1));
        assertThat(graduating).containsExactly(
                "4학년 겨울방학 (12~2월)", "졸업 후 취업 준비", "졸업 후 취업 준비");

        // grade가 없으면 상대적 라벨을 그대로 쓴다.
        String[] noGrade = ReflectionTestUtils.invokeMethod(
                roadmapService, "computeFallbackPeriods", (Integer) null, java.time.LocalDate.of(2026, 4, 15));
        assertThat(noGrade).containsExactly("1~2개월 차", "3~4개월 차", "5~6개월 차");
    }

    /**
     * 폴백 로드맵이 활동을 리스트 인덱스(0~2/3~5/6~8)로만 3등분해 2·3번째 시기에도
     * matchedActivities를 채워 넣던 회귀를 고정한다. activeActivities는 deadline ASC로
     * 정렬돼 오므로, 시기 라벨이 today 기준 실제 달력 구간(computeFallbackPeriods)이 된
     * 지금은 "가장 빨리 마감되는 활동"이 "가장 먼 미래 시기" 밑에 나올 수 있었다 — 예:
     * 8월에 마감하는 활동이 "3학년 겨울방학 (12~2월)" 카드에 실려 마감일과 시기 라벨이
     * 정면으로 모순됐다. 이제 1번째(HIGH="지금 집중") 시기에만 실제 DB 활동을 붙이고
     * 2·3번째는 텍스트 가이드만 보여준다.
     */
    @Test
    void 폴백_로드맵은_1번째_시기에만_실제_DB_활동을_붙인다() throws Exception {
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);

        when(roadmapCacheRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(targetJobRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(recommendationRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(similarSpecFinder.find(any(), any(), any())).thenReturn(List.of());

        List<Activity> activities = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            activities.add(Activity.builder()
                    .id(UUID.randomUUID())
                    .type("EXTERNAL")
                    .name("활동" + i)
                    .deadline(java.time.LocalDate.now().plusDays(i))
                    .isActive(true)
                    .build());
        }
        when(activityRepository.findRecommendableActivities(any(), any())).thenReturn(activities);

        when(promptDataBuilder.serializeSpecForRoadmap(any())).thenReturn("{}");
        when(promptDataBuilder.buildTargetJobString(any())).thenReturn("미설정");
        when(promptDataBuilder.buildSimilarCasesText(any())).thenReturn("");
        when(promptDataBuilder.buildAvailableActivitiesJson(any())).thenReturn("[]");

        // Gemini 응답이 계속 비어 있어 폴백 경로로 떨어지게 한다.
        when(geminiService.generateRoadmap(any(), any(), any(), any(), any(), any())).thenReturn("");

        ReflectionTestUtils.setField(roadmapService, "objectMapper", new ObjectMapper());

        RoadmapResponse response = roadmapService.getRoadmap(authentication);

        assertThat(response.getTimeline()).hasSize(3);
        assertThat(response.getTimeline().get(0).getMatchedActivities())
                .as("1번째(HIGH) 시기에만 실제 DB 활동이 붙어야 한다")
                .hasSize(3);
        assertThat(response.getTimeline().get(1).getMatchedActivities())
                .as("2번째 시기 라벨과 무관한 활동을 잘못 붙이지 않는다")
                .isEmpty();
        assertThat(response.getTimeline().get(2).getMatchedActivities())
                .as("3번째 시기 라벨과 무관한 활동을 잘못 붙이지 않는다")
                .isEmpty();
    }

    /**
     * 캐시된 로드맵의 모든 스텝에서 matchedActivities가 비어 있으면(activity 텍스트는 있지만
     * 실제로 매칭된 DB 활동이 하나도 없는 경우) hasUsableCachedActivities가 "사용 가능"으로
     * 오판정하던 회귀를 고정한다. 빈 스트림에 대한 noneMatch(...)는 항상 true를 반환하는
     * vacuous truth라서, 빈 목록 가드 없이는 "마감 지난 활동이 하나도 없다"와 "매칭된 활동
     * 자체가 하나도 없다"를 구분하지 못했다. RecommendationService.hasUsableCachedActivities는
     * 이미 !activities.isEmpty()로 이 케이스를 막고 있었는데, RoadmapService 쪽엔 이 가드가
     * 없었다.
     */
    @Test
    void 매칭된_활동이_하나도_없는_캐시는_사용_가능으로_판정하지_않는다() {
        RoadmapResponse allEmpty = RoadmapResponse.builder()
                .aiRoadmap(true)
                .timeline(List.of(
                        com.career.recommendation.dto.roadmap.RoadmapResponse.TimelineStep.builder()
                                .period("1~2개월 차").priority("HIGH")
                                .activity("자격증 취득 및 포트폴리오 구체화").reason("가이드")
                                .matchedActivities(List.of())
                                .build()
                ))
                .build();

        boolean usable = ReflectionTestUtils.invokeMethod(
                roadmapService, "hasUsableCachedActivities", allEmpty, java.time.LocalDate.of(2026, 8, 11));

        assertThat(usable)
                .as("매칭된 DB 활동이 하나도 없는 캐시는 재생성 대상이어야 한다")
                .isFalse();

        // 대조군: 마감 안 지난 활동이 하나라도 있으면 정상적으로 사용 가능 판정한다.
        RoadmapResponse withActivity = RoadmapResponse.builder()
                .aiRoadmap(true)
                .timeline(List.of(
                        com.career.recommendation.dto.roadmap.RoadmapResponse.TimelineStep.builder()
                                .period("1~2개월 차").priority("HIGH")
                                .activity("정보처리기사 취득").reason("가점")
                                .matchedActivities(List.of(
                                        com.career.recommendation.dto.roadmap.RoadmapResponse.MatchedActivity.builder()
                                                .activityId(UUID.randomUUID())
                                                .name("정보처리기사")
                                                .deadline(java.time.LocalDate.of(2026, 9, 1))
                                                .build()
                                ))
                                .build()
                ))
                .build();

        boolean usableWithActivity = ReflectionTestUtils.invokeMethod(
                roadmapService, "hasUsableCachedActivities", withActivity, java.time.LocalDate.of(2026, 8, 11));

        assertThat(usableWithActivity).isTrue();
    }
}
