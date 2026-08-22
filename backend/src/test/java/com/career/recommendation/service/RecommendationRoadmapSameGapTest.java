package com.career.recommendation.service;

import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.repository.PasserDataRepository;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserRepository;
import com.career.recommendation.repository.UserSpecRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * v9의 핵심 약속 — "추천과 로드맵이 같은 갭을 보고 말한다" — 를 실제 DB·캐시·서비스 조립 위에서 고정한다.
 * Gemini만 목으로 막고, 두 서비스가 프롬프트에 넣는 [합격자 비교 데이터] 텍스트를 잡아 비교한다.
 * 둘 중 한쪽이 SpecPositionService를 거치지 않고 자체 계산을 하기 시작하면 여기서 깨진다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class RecommendationRoadmapSameGapTest {

    @Autowired private RecommendationService recommendationService;
    @Autowired private RoadmapService roadmapService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSpecRepository userSpecRepository;
    @Autowired private TargetJobRepository targetJobRepository;
    @Autowired private PasserDataRepository passerDataRepository;
    @Autowired private CacheManager cacheManager;
    @MockBean private GeminiService geminiService;

    private Authentication auth;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(n -> cacheManager.getCache(n).clear());
        // Gemini는 항상 빈 응답 → 두 서비스 모두 폴백 경로. 프롬프트 입력만 검증하면 되므로 충분하다.
        when(geminiService.generateRecommendation(any(), any(), any(), any(), any())).thenReturn("");
        when(geminiService.generateRoadmap(any(), any(), any(), any(), any(), any(), any())).thenReturn("");

        User user = userRepository.save(User.builder()
                .nickname("samegap").provider("KAKAO").providerId("samegap-" + System.nanoTime()).build());
        userSpecRepository.save(UserSpec.builder().user(user).gpa(new BigDecimal("3.5")).gpaMax(new BigDecimal("4.5"))
                .languageScores(List.of(Map.of("type", "TOEIC", "score", 800))).certifications(new String[0]).grade(3).build());
        targetJobRepository.save(TargetJob.builder().user(user).jobType("BACKEND").companySize("대기업").industry("IT").build());
        // 이 테스트만의 직무 코드를 쓸 수 없으니(JobType 6종 고정) 시드와 섞여도 갭 이름이 반드시 포함되도록
        // SQLD 보유 합격자 3명을 넣는다 — 보유율이 20% 아래로 떨어질 만큼 시드가 크지 않다.
        for (int i = 0; i < 3; i++) {
            passerDataRepository.save(PasserData.builder().jobType("BACKEND").year(2026)
                    .gpa(new BigDecimal("3.8")).gpaMax(new BigDecimal("4.5"))
                    .languageScores(List.of(Map.of("type", "TOEIC", "score", 900)))
                    .certifications(new String[]{"SQLD"}).experienceCount(2)
                    .isVerified(true).dataOrigin(PasserData.ORIGIN_USER_REPORT).build());
        }
        auth = new UsernamePasswordAuthenticationToken(user.getId(), null, List.of());
    }

    @Test
    void 추천과_로드맵은_같은_위치_갭_텍스트를_Gemini에_넘긴다() {
        recommendationService.getRecommendations(auth);
        roadmapService.getRoadmap(auth);

        ArgumentCaptor<String> recContext = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> roadmapContext = ArgumentCaptor.forClass(String.class);
        // 빈 응답이면 1회 재시도하므로 호출은 2회일 수 있다 — 모든 호출의 컨텍스트가 같아야 한다.
        verify(geminiService, atLeastOnce()).generateRecommendation(any(), any(), recContext.capture(), any(), any());
        verify(geminiService, atLeastOnce()).generateRoadmap(any(), any(), any(), roadmapContext.capture(), any(), any(), any());

        assertThat(recContext.getAllValues()).isNotEmpty().containsOnly(roadmapContext.getValue());
        assertThat(roadmapContext.getAllValues()).containsOnly(recContext.getValue());
        assertThat(recContext.getValue())
                .contains("targetGap에 쓸 수 있는 갭 이름(우선순위 순):")
                .contains("SQLD");
    }
}
