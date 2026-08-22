package com.career.recommendation.service;

import com.career.recommendation.dto.position.SpecPositionResult;
import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.entity.Recommendation;
import com.career.recommendation.entity.User;
import com.career.recommendation.repository.RecommendationRepository;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserSpecRepository;
import com.career.recommendation.util.SpecPositionCalculator;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 옛 형식(scoreFormulaVersion 없음, 구 점수 체계 등)의 캐시가 하루 3회 갱신 제한을 우회하던
 * 버그를 고정한다.
 *
 * isLegacyCache 분기는 예전엔 하루 제한 체크 없이 무조건 needsNewAiCall=true였다("구버전
 * 캐시는 한 번 재생성"이라는 의도). 그런데 폴백 응답(Gemini 실패 시)은 캐시에 저장되지
 * 않으므로, Gemini가 계속 실패하거나 키가 없으면 "한 번"이 아니라 그 유저가 요청할 때마다
 * 매번 legacy 판정 → Gemini 2회 호출 → 폴백 → 캐시 미저장 → 다음 요청도 다시 legacy가
 * 반복돼 스스로 끝나지 않았다. scoreFormulaVersion을 올려 배포하면 전 유저 캐시가 동시에
 * legacy가 되므로 실제로 밟을 수 있는 경로다 — v9(위치·갭 체계) 배포가 정확히 이 상황이다.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceLegacyCacheTest {

    @Mock private CurrentUserService currentUserService;
    @Mock private UserSpecRepository userSpecRepository;
    @Mock private TargetJobRepository targetJobRepository;
    @Mock private RecommendationRepository recommendationRepository;
    @Mock private RecommendationCacheService recommendationCacheService;
    @Mock private GeminiService geminiService;
    @Mock private JobSpecProfileService jobSpecProfileService;
    @Mock private SpecPositionCalculator specPositionCalculator;
    @Mock private Authentication authentication;
    @Mock private User user;

    @InjectMocks private RecommendationService recommendationService;

    @Test
    void legacy_캐시여도_하루_갱신_제한에_도달하면_Gemini를_다시_호출하지_않는다() {
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        // 구 점수 체계(v8 이하)의 캐시 JSON — specPosition·scoreFormulaVersion이 없어
        // legacy 판정을 받는다. (구 필드(matchScore 등)를 굳이 넣지 않는 이유: 이 테스트는
        // 스프링 자동구성과 달리 순수 ObjectMapper를 쓰는데, 순수 기본값은
        // FAIL_ON_UNKNOWN_PROPERTIES가 켜져 있어 미지 필드가 있으면 역직렬화 자체가 실패해
        // "legacy 캐시"가 아니라 "파싱 불가 캐시" 경로를 타버린다 — 검증하려는 분기가 달라진다.)
        Recommendation cached = Recommendation.builder()
                .id(UUID.randomUUID())
                .user(user)
                .resultJson("{\"activities\":[]}")
                .createdAt(LocalDateTime.now())
                .lastUpdatedDate(today)
                .dailyUpdateCount(3) // 이미 하루 한도(3회)를 다 씀
                .build();
        when(recommendationRepository.findByUser_Id(userId)).thenReturn(Optional.of(cached));
        when(userSpecRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(targetJobRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        // 실제 서비스가 쓰는 ObjectMapper는 @RequiredArgsConstructor로 주입되므로 Mockito가
        // 자동 생성한 필드를 그대로 두면 안 되고, 진짜 ObjectMapper로 교체해 실제 역직렬화
        // 경로를 태운다(이 테스트의 핵심이 "역직렬화된 legacy 응답을 어떻게 처리하는가"라서).
        org.springframework.test.util.ReflectionTestUtils.setField(
                recommendationService, "objectMapper", new ObjectMapper());

        // 한도에 막혀 캐시를 주더라도, 위치·갭은 로컬 계산이므로 현재 스펙 기준으로
        // 다시 계산해서 준다(rebuildPositionFromCurrentSpec 경로).
        SpecPositionResult freshPosition = SpecPositionResult.builder()
                .basis("NONE")
                .basisMessage("현재 스펙 기준 메시지")
                .sampleSize(0)
                .axes(List.of())
                .gaps(List.of())
                .build();
        when(jobSpecProfileService.getJobProfile(any())).thenReturn(null);
        when(jobSpecProfileService.getOverallProfile()).thenReturn(null);
        when(specPositionCalculator.calculate(any(), any(), any())).thenReturn(freshPosition);

        RecommendationResponse response = recommendationService.getRecommendations(authentication);

        // 핵심 불변식: 하루 제한에 도달했으므로 legacy든 아니든 Gemini를 다시 부르지 않는다.
        verify(geminiService, never()).generateRecommendation(any(), any(), any(), any(), any());
        // 위치·갭은 옛 캐시 값이 아니라 현재 스펙으로 재계산된 값이다 —
        // 예전엔 캐시를 통째로 반환해 스펙을 바꾼 사용자에게 옛 결과가 그대로 보였다.
        assertThat(response.getSpecPosition()).isNotNull();
        assertThat(response.getSpecPosition().getBasisMessage()).isEqualTo("현재 스펙 기준 메시지");
        assertThat(response.getScoreFormulaVersion())
                .isEqualTo(SpecPositionCalculator.CURRENT_SCORE_FORMULA_VERSION);
        // FE가 "오늘 갱신 횟수를 모두 사용했어요"를 안내할 수 있도록 플래그가 붙는다.
        assertThat(response.getDailyLimitReached()).isTrue();
    }
}
