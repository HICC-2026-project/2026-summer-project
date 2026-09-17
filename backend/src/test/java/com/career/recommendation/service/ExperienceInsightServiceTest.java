package com.career.recommendation.service;

import com.career.recommendation.dto.experience.ExperienceAnswerRequest;
import com.career.recommendation.dto.experience.ExperienceEnrichRequest;
import com.career.recommendation.dto.experience.ExperienceEnrichResponse;
import com.career.recommendation.dto.experience.ExperienceQuestionsRequest;
import com.career.recommendation.dto.experience.ExperienceQuestionsResponse;
import com.career.recommendation.dto.user.ExperienceRequest;
import com.career.recommendation.entity.User;
import com.career.recommendation.exception.AiDailyLimitExceededException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E11-6 — 경험 심층 질문·보강. Gemini mock으로 정상·JSON 깨짐 폴백·미지 area 필터·depth
 * 검증·리미터 초과를 고정한다. 답변 원문은 어디에도 저장하지 않는 stateless 서비스라
 * DB/캐시 관련 목은 없다.
 */
@ExtendWith(MockitoExtension.class)
class ExperienceInsightServiceTest {

    @Mock private CurrentUserService currentUserService;
    @Mock private GeminiService geminiService;
    @Mock private AiDailyAttemptLimiter aiDailyAttemptLimiter;
    @Mock private Authentication authentication;
    @Mock private User user;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private ExperienceInsightService experienceInsightService;

    private void givenAuthenticatedUserWithinLimit() {
        UUID userId = UUID.randomUUID();
        lenient().when(user.getId()).thenReturn(userId);
        lenient().when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        lenient().when(aiDailyAttemptLimiter.tryAcquireEnrich(userId)).thenReturn(true);
    }

    // --- 질문 생성 ---

    @Test
    void 정상_응답이면_질문_목록을_그대로_반환한다() {
        givenAuthenticatedUserWithinLimit();
        when(geminiService.generateExperienceQuestions(anyString()))
                .thenReturn("{\"questions\": [\"토큰 만료는 어떻게 처리했나요?\", \"동시성 이슈는 없었나요?\"]}");

        ExperienceQuestionsResponse response =
                experienceInsightService.generateQuestions(authentication, questionsRequest());

        assertThat(response.getQuestions())
                .containsExactly("토큰 만료는 어떻게 처리했나요?", "동시성 이슈는 없었나요?");
    }

    @Test
    void 질문이_3개를_초과해도_3개까지만_남긴다() {
        givenAuthenticatedUserWithinLimit();
        when(geminiService.generateExperienceQuestions(anyString()))
                .thenReturn("{\"questions\": [\"Q1\", \"Q2\", \"Q3\", \"Q4\"]}");

        ExperienceQuestionsResponse response =
                experienceInsightService.generateQuestions(authentication, questionsRequest());

        assertThat(response.getQuestions()).hasSize(3).containsExactly("Q1", "Q2", "Q3");
    }

    @Test
    void Gemini가_빈_문자열을_반환하면_질문_없음으로_폴백한다() {
        // GeminiService.callGeminiApi는 전역 GeminiDailyQuota 소진·API 키 미설정·호출 실패
        // 모두 빈 문자열로 통일해서 반환한다 — 이 서비스는 그 셋을 구분하지 않고 폴백한다.
        givenAuthenticatedUserWithinLimit();
        when(geminiService.generateExperienceQuestions(anyString())).thenReturn("");

        ExperienceQuestionsResponse response =
                experienceInsightService.generateQuestions(authentication, questionsRequest());

        assertThat(response.getQuestions()).isEmpty();
    }

    @Test
    void JSON이_깨져있으면_질문_없음으로_폴백한다() {
        givenAuthenticatedUserWithinLimit();
        when(geminiService.generateExperienceQuestions(anyString())).thenReturn("이건 JSON이 아님");

        ExperienceQuestionsResponse response =
                experienceInsightService.generateQuestions(authentication, questionsRequest());

        assertThat(response.getQuestions()).isEmpty();
    }

    @Test
    void Gemini_호출_자체가_예외를_던져도_질문_없음으로_폴백한다() {
        givenAuthenticatedUserWithinLimit();
        when(geminiService.generateExperienceQuestions(anyString()))
                .thenThrow(new RuntimeException("네트워크 오류"));

        ExperienceQuestionsResponse response =
                experienceInsightService.generateQuestions(authentication, questionsRequest());

        assertThat(response.getQuestions()).isEmpty();
    }

    @Test
    void 질문_생성도_사용자별_하루_상한을_초과하면_예외를_던진다() {
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(aiDailyAttemptLimiter.tryAcquireEnrich(userId)).thenReturn(false);

        assertThatThrownBy(() -> experienceInsightService.generateQuestions(authentication, questionsRequest()))
                .isInstanceOf(AiDailyLimitExceededException.class);

        verify(geminiService, never()).generateExperienceQuestions(any());
    }

    // --- 보강 ---

    @Test
    void 정상_응답이면_areas_depth_roleSummary를_그대로_반환한다() {
        givenAuthenticatedUserWithinLimit();
        when(geminiService.generateExperienceEnrichment(anyString(), anyString()))
                .thenReturn("{\"areas\": [\"API\", \"DB\"], \"depth\": \"IMPLEMENTED\", " +
                        "\"roleSummary\": \"Spring 기반 결제 API를 직접 설계·구현\"}");

        ExperienceEnrichResponse response = experienceInsightService.enrich(authentication, enrichRequest());

        assertThat(response.getAreas()).containsExactly("API", "DB");
        assertThat(response.getDepth()).isEqualTo("IMPLEMENTED");
        assertThat(response.getRoleSummary()).isEqualTo("Spring 기반 결제 API를 직접 설계·구현");
    }

    @Test
    void 미지_area_코드는_필터링되고_대소문자는_정규화된다() {
        givenAuthenticatedUserWithinLimit();
        when(geminiService.generateExperienceEnrichment(anyString(), anyString()))
                .thenReturn("{\"areas\": [\"api\", \"NOT_A_REAL_AREA\", \"db\", \"db\"], " +
                        "\"depth\": \"implemented\", \"roleSummary\": \"요약\"}");

        ExperienceEnrichResponse response = experienceInsightService.enrich(authentication, enrichRequest());

        // 대소문자 무시 정규화 + 중복 제거 + 미지 코드(NOT_A_REAL_AREA) 필터링
        assertThat(response.getAreas()).containsExactly("API", "DB");
        assertThat(response.getDepth()).isEqualTo("IMPLEMENTED");
    }

    @Test
    void depth가_허용값이_아니면_null로_대체된다() {
        givenAuthenticatedUserWithinLimit();
        when(geminiService.generateExperienceEnrichment(anyString(), anyString()))
                .thenReturn("{\"areas\": [], \"depth\": \"FULLY_MASTERED\", \"roleSummary\": \"요약\"}");

        ExperienceEnrichResponse response = experienceInsightService.enrich(authentication, enrichRequest());

        assertThat(response.getDepth()).isNull();
    }

    @Test
    void roleSummary가_100자를_초과하면_100자로_컷된다() {
        givenAuthenticatedUserWithinLimit();
        String longSummary = "가".repeat(150);
        when(geminiService.generateExperienceEnrichment(anyString(), anyString()))
                .thenReturn("{\"areas\": [], \"depth\": null, \"roleSummary\": \"" + longSummary + "\"}");

        ExperienceEnrichResponse response = experienceInsightService.enrich(authentication, enrichRequest());

        assertThat(response.getRoleSummary()).hasSize(100);
    }

    @Test
    void JSON이_깨져있으면_모두_빈값으로_폴백한다() {
        givenAuthenticatedUserWithinLimit();
        when(geminiService.generateExperienceEnrichment(anyString(), anyString())).thenReturn("문법 깨진 응답 {{{");

        ExperienceEnrichResponse response = experienceInsightService.enrich(authentication, enrichRequest());

        assertThat(response.getAreas()).isEmpty();
        assertThat(response.getDepth()).isNull();
        assertThat(response.getRoleSummary()).isNull();
    }

    @Test
    void Gemini가_빈_문자열을_반환하면_모두_빈값으로_폴백한다() {
        givenAuthenticatedUserWithinLimit();
        when(geminiService.generateExperienceEnrichment(anyString(), anyString())).thenReturn("");

        ExperienceEnrichResponse response = experienceInsightService.enrich(authentication, enrichRequest());

        assertThat(response.getAreas()).isEmpty();
        assertThat(response.getDepth()).isNull();
        assertThat(response.getRoleSummary()).isNull();
    }

    @Test
    void 보강도_사용자별_하루_상한을_초과하면_예외를_던진다() {
        UUID userId = UUID.randomUUID();
        when(user.getId()).thenReturn(userId);
        when(currentUserService.getCurrentUser(authentication)).thenReturn(user);
        when(aiDailyAttemptLimiter.tryAcquireEnrich(userId)).thenReturn(false);

        assertThatThrownBy(() -> experienceInsightService.enrich(authentication, enrichRequest()))
                .isInstanceOf(AiDailyLimitExceededException.class);

        verify(geminiService, never()).generateExperienceEnrichment(any(), any());
    }

    // --- 헬퍼 ---

    private ExperienceQuestionsRequest questionsRequest() {
        ExperienceQuestionsRequest request = new ExperienceQuestionsRequest();
        ExperienceRequest experience = new ExperienceRequest();
        experience.setType("PROJECT");
        experience.setTitle("결제 API 서버");
        experience.setRole("백엔드 담당");
        experience.setStack(List.of("Spring Boot", "Redis"));
        request.setExperience(experience);
        return request;
    }

    private ExperienceEnrichRequest enrichRequest() {
        ExperienceEnrichRequest request = new ExperienceEnrichRequest();
        ExperienceRequest experience = new ExperienceRequest();
        experience.setType("PROJECT");
        experience.setTitle("결제 API 서버");
        request.setExperience(experience);
        ExperienceAnswerRequest answer = new ExperienceAnswerRequest();
        answer.setQuestion("토큰 만료는 어떻게 처리했나요?");
        answer.setAnswer("Redis에 TTL을 걸어 자동 만료시켰습니다.");
        request.setAnswers(List.of(answer));
        return request;
    }
}
