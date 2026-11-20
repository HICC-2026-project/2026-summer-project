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

    /**
     * 함수 이름: generateQuestions()
     * 기능: 경험 입력 내용을 바탕으로 Gemini에게 후속 질문(최대 3개)을 만들게 한다. 사용자별 일일 상한을 먼저 차감하며,
     *       Gemini 호출 실패 시 빈 질문 목록으로 응답한다(200 폴백).
     * 파라미터: Authentication authentication -> 현재 로그인 사용자 인증 정보
     *           ExperienceQuestionsRequest request -> 질문 생성 대상 경험 정보
     * 반환값: ExperienceQuestionsResponse -> 후속 질문 목록
     * 예외: AiDailyLimitExceededException -> 사용자별 하루 상한(ENRICH) 초과 시(429)
     */
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

    /**
     * 함수 이름: enrich()
     * 기능: 경험 정보와 사용자의 질문 답변을 Gemini에 보내 areas·depth·roleSummary를 판정한다.
     *       사용자별 일일 상한을 먼저 차감하며, Gemini 호출 실패 시 빈 응답을 돌려준다(200 폴백).
     *       DB에는 아무것도 쓰지 않는다.
     * 파라미터: Authentication authentication -> 현재 로그인 사용자 인증 정보
     *           ExperienceEnrichRequest request -> 경험 정보와 질문·답변 목록
     * 반환값: ExperienceEnrichResponse -> 보강된 영역·깊이·역할 요약
     * 예외: AiDailyLimitExceededException -> 사용자별 하루 상한(ENRICH) 초과 시(429)
     */
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

    /**
     * 함수 이름: acquireOrThrow()
     * 기능: 사용자의 ENRICH 일일 시도 1회를 차감하고, 상한을 넘었으면 예외를 던진다.
     * 파라미터: User user -> 현재 사용자
     * 반환값: 없음
     * 예외: AiDailyLimitExceededException -> 하루 10회 상한 초과 시
     */
    private void acquireOrThrow(User user) {
        if (!aiDailyAttemptLimiter.tryAcquireEnrich(user.getId())) {
            throw new AiDailyLimitExceededException(
                    "경험 심층 질문·보강 요청은 하루 10회까지 가능합니다. 내일 다시 시도해 주세요.");
        }
    }

    /**
     * 함수 이름: buildExperienceContext()
     * 기능: ExperienceRequest를 Gemini 프롬프트에 넣을 텍스트로 변환한다. 선택 필드는 값이 있을 때만 넣는다.
     * 파라미터: ExperienceRequest experience -> 경험 입력 정보
     * 반환값: String -> 프롬프트용 경험 설명 텍스트
     */
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

    /**
     * 함수 이름: buildQaContext()
     * 기능: 질문·답변 목록을 번호가 붙은 "Q/A" 텍스트로 이어 붙인다. null 항목은 건너뛴다.
     * 파라미터: List<ExperienceAnswerRequest> answers -> 질문과 답변 목록
     * 반환값: String -> 프롬프트용 질문·답변 텍스트
     */
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

    /**
     * 함수 이름: appendIfPresent()
     * 기능: 값이 null이 아니고 공백이 아닐 때만 "라벨: 값" 한 줄을 StringBuilder에 덧붙인다.
     * 파라미터: StringBuilder sb -> 덧붙일 대상 버퍼
     *           String label -> 항목 이름
     *           String value -> 항목 값
     * 반환값: 없음
     */
    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append('\n');
        }
    }

    /**
     * 함수 이름: blankToDefault()
     * 기능: 값이 null이거나 공백이면 기본값을, 아니면 원래 값을 돌려준다.
     * 파라미터: String value -> 검사할 값
     *           String fallback -> 비었을 때 쓸 기본값
     * 반환값: String -> value 또는 fallback
     */
    private String blankToDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /**
     * 함수 이름: parseQuestions()
     * 기능: Gemini가 돌려준 JSON에서 질문 목록을 꺼내 공백 제거 후 최대 3개까지만 남긴다.
     *       응답이 비었거나 파싱에 실패하면 빈 목록을 돌려준다.
     * 파라미터: String rawJson -> Gemini 응답 JSON 문자열
     * 반환값: List<String> -> 정제된 질문 목록
     */
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

    /**
     * 함수 이름: parseEnrichment()
     * 기능: Gemini 보강 응답 JSON을 파싱해 areas·depth·roleSummary를 각각 검증·정제한 응답으로 만든다.
     *       응답이 비었거나 파싱에 실패하면 빈 응답을 돌려준다.
     * 파라미터: String rawJson -> Gemini 응답 JSON 문자열
     * 반환값: ExperienceEnrichResponse -> 정제된 보강 결과
     */
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

    /**
     * 함수 이름: filterAreas()
     * 기능: ExperienceArea 13종 코드만 남긴다(대소문자 무시, 중복 제거). 미지 코드는 조용히 버린다.
     * 파라미터: List<String> raw -> Gemini가 준 영역 문자열 목록
     * 반환값: List<String> -> 유효한 영역 코드(enum 이름) 목록
     */
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

    /**
     * 함수 이름: filterDepth()
     * 기능: IMPLEMENTED|CONFIGURED|BOILERPLATE만 허용(대소문자 무시). 그 외는 null로 폐기.
     * 파라미터: String raw -> Gemini가 준 깊이 문자열
     * 반환값: String -> 대문자로 정규화된 깊이 값, 유효하지 않으면 null
     */
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

    /**
     * 함수 이름: filterRoleSummary()
     * 기능: 역할 요약을 공백 제거 후 100자로 자른다. 빈 값이면 null.
     * 파라미터: String raw -> Gemini가 준 역할 요약
     * 반환값: String -> 정제된 요약(최대 100자), 비어 있으면 null
     */
    private String filterRoleSummary(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
    }
}
