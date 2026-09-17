package com.career.recommendation.service;

import com.career.recommendation.domain.ExperienceArea;
import com.career.recommendation.dto.experience.ExperienceAnswerRequest;
import com.career.recommendation.dto.experience.ExperienceEnrichRequest;
import com.career.recommendation.dto.experience.ExperienceEnrichResponse;
import com.career.recommendation.dto.experience.ExperienceQuestionsRequest;
import com.career.recommendation.dto.experience.ExperienceQuestionsResponse;
import com.career.recommendation.dto.gemini.GeminiExperienceEnrichmentResult;
import com.career.recommendation.dto.gemini.GeminiExperienceQuestionsResult;
import com.career.recommendation.dto.user.ExperienceRequest;
import com.career.recommendation.entity.User;
import com.career.recommendation.exception.AiDailyLimitExceededException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * E11-6 — 경험 입력 내용을 바탕으로 Gemini 후속 질문을 만들고, 답변으로 areas·depth·
 * roleSummary를 판정하는 stateless 서비스. DB에 아무것도 쓰지 않는다 — 결과는 FE가
 * 받아서 기존 PUT /users/me/spec으로 병합·저장한다. 답변 원문은 이 서비스 안에서만
 * 쓰이고 응답을 만든 뒤 폐기된다(로그에도 남기지 않는다).
 *
 * 상한 처리 방식(BACKLOG.md 원문의 "실패·상한 초과 폴백: 200"과 "초과 시 429" 사이의
 * 표현 차이를 아래처럼 해소했다 — 두 상한의 층이 다르기 때문에 둘 다 참일 수 있다):
 *   - 사용자별 하루 상한(AiDailyAttemptLimiter, kind=ENRICH, 기본 10회) 초과 → 429
 *     (AiDailyLimitExceededException). 이 상한은 남용 방지용이라 명시적으로 알려야 한다.
 *   - 전역 Gemini 일일 상한(GeminiDailyQuota) 소진·Gemini 호출/파싱 실패 → 200 + 빈 값.
 *     GeminiService.callGeminiApi가 이미 이 두 경우 모두 빈 문자열을 반환하도록 만들어져
 *     있어(전역 상한 미달 시 즉시 "", 예외 시에도 catch 후 ""), 별도 분기 없이 자연히
 *     Gemini "실패" 경로로 흘러 폴백 응답이 나간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperienceInsightService {

    /** 질문은 2~3개만 쓴다는 계약을 방어적으로 강제한다 — Gemini가 그 이상을 줘도 자른다. */
    private static final int MAX_QUESTIONS = 3;

    private final CurrentUserService currentUserService;
    private final GeminiService geminiService;
    private final AiDailyAttemptLimiter aiDailyAttemptLimiter;
    private final ObjectMapper objectMapper;

    public ExperienceQuestionsResponse generateQuestions(
            Authentication authentication, ExperienceQuestionsRequest request) {
        User user = currentUserService.getCurrentUser(authentication);
        acquireOrThrow(user);

        String experienceContext = buildExperienceContext(request.getExperience());
        String rawJson;
        try {
            rawJson = geminiService.generateExperienceQuestions(experienceContext);
        } catch (Exception e) {
            log.warn("경험 질문 생성 Gemini 호출 실패: {}", e.getMessage());
            return ExperienceQuestionsResponse.empty();
        }
        return ExperienceQuestionsResponse.builder()
                .questions(parseQuestions(rawJson))
                .build();
    }

    public ExperienceEnrichResponse enrich(Authentication authentication, ExperienceEnrichRequest request) {
        User user = currentUserService.getCurrentUser(authentication);
        acquireOrThrow(user);

        String experienceContext = buildExperienceContext(request.getExperience());
        String qaContext = buildQaContext(request.getAnswers());
        String rawJson;
        try {
            rawJson = geminiService.generateExperienceEnrichment(experienceContext, qaContext);
        } catch (Exception e) {
            log.warn("경험 보강 Gemini 호출 실패: {}", e.getMessage());
            return ExperienceEnrichResponse.empty();
        }
        return parseEnrichment(rawJson);
    }

    private void acquireOrThrow(User user) {
        if (!aiDailyAttemptLimiter.tryAcquireEnrich(user.getId())) {
            throw new AiDailyLimitExceededException(
                    "경험 심층 질문·보강 요청은 하루 10회까지 가능합니다. 내일 다시 시도해 주세요.");
        }
    }

    /** ExperienceRequest를 Gemini 프롬프트에 넣을 텍스트로 변환한다. 선택 필드는 값이 있을 때만 넣는다. */
    private String buildExperienceContext(ExperienceRequest experience) {
        StringBuilder sb = new StringBuilder();
        sb.append("유형: ").append(blankToDefault(experience.getType(), "미지정")).append('\n');
        sb.append("제목: ").append(experience.getTitle()).append('\n');
        appendIfPresent(sb, "설명", experience.getDescription());
        appendIfPresent(sb, "역할", experience.getRole());
        if (experience.getStack() != null && !experience.getStack().isEmpty()) {
            sb.append("사용 기술: ").append(String.join(", ", experience.getStack())).append('\n');
        }
        if (experience.getAreas() != null && !experience.getAreas().isEmpty()) {
            sb.append("현재 태그된 영역: ").append(String.join(", ", experience.getAreas())).append('\n');
        }
        if (experience.getMonths() != null) {
            sb.append("활동 기간: ").append(experience.getMonths()).append("개월\n");
        }
        return sb.toString();
    }

    private String buildQaContext(List<ExperienceAnswerRequest> answers) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (ExperienceAnswerRequest answer : answers) {
            if (answer == null) continue;
            sb.append(index++).append(". Q: ").append(answer.getQuestion())
                    .append("\n   A: ").append(answer.getAnswer()).append('\n');
        }
        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append('\n');
        }
    }

    private String blankToDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private List<String> parseQuestions(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            GeminiExperienceQuestionsResult result =
                    objectMapper.readValue(rawJson, GeminiExperienceQuestionsResult.class);
            if (result.getQuestions() == null) {
                return List.of();
            }
            List<String> cleaned = new ArrayList<>();
            for (String question : result.getQuestions()) {
                if (question != null && !question.isBlank()) {
                    cleaned.add(question.trim());
                }
                if (cleaned.size() >= MAX_QUESTIONS) break;
            }
            return cleaned;
        } catch (Exception e) {
            log.warn("경험 질문 응답 파싱 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private ExperienceEnrichResponse parseEnrichment(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return ExperienceEnrichResponse.empty();
        }
        try {
            GeminiExperienceEnrichmentResult result =
                    objectMapper.readValue(rawJson, GeminiExperienceEnrichmentResult.class);
            return ExperienceEnrichResponse.builder()
                    .areas(filterAreas(result.getAreas()))
                    .depth(filterDepth(result.getDepth()))
                    .roleSummary(filterRoleSummary(result.getRoleSummary()))
                    .build();
        } catch (Exception e) {
            log.warn("경험 보강 응답 파싱 실패: {}", e.getMessage());
            return ExperienceEnrichResponse.empty();
        }
    }

    /** ExperienceArea 13종 코드만 남긴다(대소문자 무시, 중복 제거). 미지 코드는 조용히 버린다. */
    private List<String> filterAreas(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String value : raw) {
            if (value == null) continue;
            ExperienceArea.from(value).ifPresent(area -> result.add(area.name()));
        }
        return new ArrayList<>(result);
    }

    /** IMPLEMENTED|CONFIGURED|BOILERPLATE만 허용(대소문자 무시). 그 외는 null로 폐기. */
    private String filterDepth(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "IMPLEMENTED", "CONFIGURED", "BOILERPLATE" -> normalized;
            default -> null;
        };
    }

    /** 100자 컷. 빈 값이면 null. */
    private String filterRoleSummary(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
    }
}
