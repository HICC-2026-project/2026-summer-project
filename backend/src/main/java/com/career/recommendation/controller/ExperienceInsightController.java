package com.career.recommendation.controller;

import com.career.recommendation.config.SwaggerConfig;
import com.career.recommendation.dto.experience.ExperienceEnrichRequest;
import com.career.recommendation.dto.experience.ExperienceEnrichResponse;
import com.career.recommendation.dto.experience.ExperienceQuestionsRequest;
import com.career.recommendation.dto.experience.ExperienceQuestionsResponse;
import com.career.recommendation.service.ExperienceInsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * E11-6 — 경험 입력 내용 기반 Gemini 후속 질문·보강. stateless(DB 변경 없음) — 서버는
 * 아무것도 저장하지 않고, FE가 결과를 경험에 병합해 기존 PUT /users/me/spec으로 저장한다.
 * 답변 원문은 응답을 만드는 데만 쓰이고 어디에도 남지 않는다.
 */
@Tag(name = "경험 심층 질문", description = "경험 입력 내용 기반 Gemini 후속 질문·보강 (E11-6, 저장 없음)")
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/users/me/experiences")
@RequiredArgsConstructor
public class ExperienceInsightController {

    private final ExperienceInsightService experienceInsightService;

    @Operation(summary = "경험 심층 질문 생성",
            description = "경험 입력 내용을 바탕으로 기술적 깊이를 판별할 후속 질문 2~3개를 생성한다. " +
                    "Gemini 실패·전역 일일 상한 소진 시 questions: []을 반환한다(FE가 질문 단계를 건너뛴다). " +
                    "사용자별 하루 상한(질문+보강 합산 10회) 초과 시 429.")
    @PostMapping("/questions")
    public ExperienceQuestionsResponse generateQuestions(
            Authentication authentication,
            @Valid @RequestBody ExperienceQuestionsRequest request
    ) {
        return experienceInsightService.generateQuestions(authentication, request);
    }

    @Operation(summary = "경험 보강(영역·깊이·역할 요약)",
            description = "후속 질문 답변(1~3개, 각 1000자 이하)을 바탕으로 areas·depth·roleSummary를 판정한다. " +
                    "답변 원문은 저장하지 않는다. Gemini 실패·전역 일일 상한 소진 시 빈 값을 반환한다. " +
                    "사용자별 하루 상한(질문+보강 합산 10회) 초과 시 429.")
    @PostMapping("/enrich")
    public ExperienceEnrichResponse enrich(
            Authentication authentication,
            @Valid @RequestBody ExperienceEnrichRequest request
    ) {
        return experienceInsightService.enrich(authentication, request);
    }
}
