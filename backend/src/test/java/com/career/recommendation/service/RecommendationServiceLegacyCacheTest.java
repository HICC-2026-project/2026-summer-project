package com.career.recommendation.service;

import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.entity.Recommendation;
import com.career.recommendation.entity.User;
import com.career.recommendation.repository.RecommendationRepository;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserSpecRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

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
 * 옛 형식(scoreFormulaVersion 없음 등)의 캐시가 하루 3회 갱신 제한을 우회하던 버그를 고정한다.
 *
 * isLegacyCache 분기는 예전엔 하루 제한 체크 없이 무조건 needsNewAiCall=true였다("구버전
 * 캐시는 한 번 재생성"이라는 의도). 그런데 폴백 응답(Gemini 실패 시)은 캐시에 저장되지
 * 않으므로, Gemini가 계속 실패하거나 키가 없으면 "한 번"이 아니라 그 유저가 요청할 때마다
 * 매번 legacy 판정 → Gemini 2회 호출 → 폴백 → 캐시 미저장 → 다음 요청도 다시 legacy가
 * 반복돼 스스로 끝나지 않았다. scoreFormulaVersion을 올려 배포하면 전 유저 캐시가 동시에
 * legacy가 되므로 실제로 밟을 수 있는 경로다.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceLegacyCacheTest {

    @Mock private CurrentUserService currentUserService;
    @Mock private UserSpecRepository userSpecRepository;
    @Mock private TargetJobRepository targetJobRepository;
    @Mock private RecommendationRepository recommendationRepository;
    @Mock private RecommendationCacheService recommendationCacheService;
    @Mock private GeminiService geminiService;
    @Mock private com.career.recommendation.util.SimilarSpecFinder similarSpecFinder;
    @Mock private GlobalCertPoolService globalCertPoolService;
    @Mock private com.career.recommendation.util.MatchScoreCalculator matchScoreCalculator;
    @Mock private Authentication authentication;
    @Mock private User user;

    @InjectMocks private RecommendationService recommendationService;

    @Test
    void legacy_캐시여도_하루_갱신_제한에_도달하면_Gemini를_다시_호출하지_않는다() {
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Recommendation cached = Recommendation.builder()
                .id(UUID.randomUUID())
                .user(user)
                .resultJson("{\"legacy\":true}")
                .createdAt(LocalDateTime.now())
                .lastUpdatedDate(today)
                .dailyUpdateCount(3) // 이미 하루 한도(3회)를 다 씀
                .build();
        when(recommendationRepository.findByUser_Id(userId)).thenReturn(Optional.of(cached));
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(targetJobRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        // scoreFormulaVersion·sampleComparisonData가 없는 옛 형식 응답 — isLegacyCache=true를
        // 만드는 조건이다.
        RecommendationResponse legacyResponse = RecommendationResponse.builder()
                .activities(List.of())
                .matchScore(55)
                .comparisonMessage("옛 캐시")
                .build();

        // 실제 서비스가 쓰는 ObjectMapper는 @RequiredArgsConstructor로 주입되므로 Mockito가
        // 자동 생성한 필드를 그대로 두면 안 되고, 진짜 ObjectMapper로 교체해 실제 역직렬화
        // 경로를 태운다(이 테스트의 핵심이 "역직렬화된 legacy 응답을 어떻게 처리하는가"라서).
        ObjectMapper realMapper = new ObjectMapper();
        try {
            cached.setResultJson(realMapper.writeValueAsString(legacyResponse));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        org.springframework.test.util.ReflectionTestUtils.setField(recommendationService, "objectMapper", realMapper);

        // 한도에 막혀 캐시를 주더라도, 점수·비교표는 로컬 계산이므로 현재 스펙 기준으로
        // 다시 계산해서 준다(rebuildComparisonFromCurrentSpec 경로).
        when(similarSpecFinder.find(any(), any(), any())).thenReturn(List.of());
        when(similarSpecFinder.buildComparisonMessage(any(), any())).thenReturn("현재 스펙 기준 메시지");
        when(globalCertPoolService.getGlobalCertPool()).thenReturn(java.util.Set.of());
        when(globalCertPoolService.getJobPasserCertRows(any())).thenReturn(List.of());
        when(matchScoreCalculator.calculate(any(), any(), any(), any()))
                .thenReturn(com.career.recommendation.dto.recommendation.MatchScoreResult.builder()
                        .totalScore(70)
                        .compareRows(List.of())
                        .build());

        RecommendationResponse response = recommendationService.getRecommendations(authentication);

        // 핵심 불변식: 하루 제한에 도달했으므로 legacy든 아니든 Gemini를 다시 부르지 않는다.
        verify(geminiService, never()).generateRecommendation(any(), any(), any(), any(), any());
        // 점수·비교 관련 필드는 옛 캐시 값(55/"옛 캐시")이 아니라 현재 스펙으로 재계산된 값이다 —
        // 예전엔 캐시를 통째로 반환해 스펙을 바꾼 사용자에게 옛 점수가 그대로 보였다.
        assertThat(response.getMatchScore()).isEqualTo(70);
        assertThat(response.getComparisonMessage()).isEqualTo("현재 스펙 기준 메시지");
        // FE가 "오늘 갱신 횟수를 모두 사용했어요"를 안내할 수 있도록 플래그가 붙는다.
        assertThat(response.getDailyLimitReached()).isTrue();
    }
}
