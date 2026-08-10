package com.career.recommendation.service;

import com.career.recommendation.dto.recommendation.MatchScoreResult;
import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.entity.User;
import com.career.recommendation.repository.ActivityRepository;
import com.career.recommendation.repository.RecommendationRepository;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserSpecRepository;
import com.career.recommendation.util.MatchScoreCalculator;
import com.career.recommendation.util.PromptDataBuilder;
import com.career.recommendation.util.SimilarSpecFinder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 추천 가능한 활동이 0건인 상태에서 Gemini까지 실패했을 때의 폴백 동작을 고정한다.
 *
 * 예전에는 buildFallbackResponse가 이 경우 null을 반환했고, 호출부가 그 값에 곧바로
 * isAiRecommendation()을 호출해 추천 API 전체가 NPE로 500이 됐다. 활동 0건은 장애가 아니라
 * (전 활동 마감, 크롤링 중단 등) 정상적으로 발생할 수 있는 상태라 200으로 응답해야 한다.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceFallbackTest {

    @Mock private CurrentUserService currentUserService;
    @Mock private UserSpecRepository userSpecRepository;
    @Mock private TargetJobRepository targetJobRepository;
    @Mock private RecommendationRepository recommendationRepository;
    @Mock private RecommendationCacheService recommendationCacheService;
    @Mock private ActivityRepository activityRepository;
    @Mock private GlobalCertPoolService globalCertPoolService;
    @Mock private SimilarSpecFinder similarSpecFinder;
    @Mock private MatchScoreCalculator matchScoreCalculator;
    @Mock private GeminiService geminiService;
    @Mock private PromptDataBuilder promptDataBuilder;
    @Mock private ObjectMapper objectMapper;
    @Mock private Authentication authentication;
    @Mock private User user;

    @InjectMocks private RecommendationService recommendationService;

    /** 활동 0건 + Gemini 장애 + 캐시 없음 상태를 만든다. */
    private void givenNoActivitiesAndGeminiDown(MatchScoreResult matchResult) {
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);

        when(recommendationRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(targetJobRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        when(similarSpecFinder.find(any(), any(), any())).thenReturn(List.of());
        when(similarSpecFinder.buildComparisonMessage(anyInt(), any())).thenReturn("비교 데이터가 부족합니다.");

        // 핵심 조건: 추천 가능한 활동이 한 건도 없다.
        when(activityRepository.findRecommendableActivities(any(), any())).thenReturn(List.of());

        when(promptDataBuilder.buildAvailableActivitiesJson(any())).thenReturn("[]");
        when(promptDataBuilder.serializeSpecForRecommendation(any())).thenReturn("{}");
        when(promptDataBuilder.buildTargetJobString(any())).thenReturn("미설정");
        when(promptDataBuilder.buildSimilarCasesText(any())).thenReturn("");

        when(globalCertPoolService.getGlobalCertPool()).thenReturn(Set.of());
        when(globalCertPoolService.getJobPasserCertRows(any())).thenReturn(List.of());

        when(geminiService.generateRecommendation(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Gemini 장애 시뮬레이션"));

        when(matchScoreCalculator.calculate(any(), any(), any(), any())).thenReturn(matchResult);
    }

    @Test
    void 추천할_활동이_0건이어도_null이_아니라_빈_활동_목록을_반환한다() {
        givenNoActivitiesAndGeminiDown(MatchScoreResult.builder()
                .totalScore(0)
                .compareRows(List.of())
                .unrecognizedCertifications(List.of())
                .build());

        RecommendationResponse response = recommendationService.getRecommendations(authentication);

        assertThat(response).isNotNull();
        assertThat(response.getActivities()).isEmpty();
        assertThat(response.isAiRecommendation()).isFalse();
        assertThat(response.getScoreFormulaVersion())
                .isEqualTo(MatchScoreCalculator.CURRENT_SCORE_FORMULA_VERSION);
    }

    @Test
    void 추천할_활동이_0건이어도_getRecommendations가_예외를_던지지_않는다() {
        givenNoActivitiesAndGeminiDown(MatchScoreResult.builder()
                .totalScore(0)
                .compareRows(List.of())
                .unrecognizedCertifications(List.of())
                .build());

        assertThatCode(() -> recommendationService.getRecommendations(authentication))
                .doesNotThrowAnyException();
    }

    @Test
    void 활동이_0건이어도_미인식_자격증_고지는_그대로_전달된다() {
        givenNoActivitiesAndGeminiDown(MatchScoreResult.builder()
                .totalScore(0)
                .compareRows(List.of())
                .unrecognizedCertifications(List.of("완전정크자격증123"))
                .build());

        RecommendationResponse response = recommendationService.getRecommendations(authentication);

        assertThat(response.getUnrecognizedCertifications()).containsExactly("완전정크자격증123");
    }

    @Test
    void 폴백_응답은_캐시에_저장되지_않는다() {
        givenNoActivitiesAndGeminiDown(MatchScoreResult.builder()
                .totalScore(0)
                .compareRows(List.of())
                .unrecognizedCertifications(List.of())
                .build());

        recommendationService.getRecommendations(authentication);

        // 폴백을 캐싱하면 활동이 다시 생겨도 빈 추천이 캐시로 굳어버린다.
        verify(recommendationCacheService, never()).save(any(), any());
    }
}
