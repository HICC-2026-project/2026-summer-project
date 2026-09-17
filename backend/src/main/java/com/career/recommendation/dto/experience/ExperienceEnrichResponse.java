package com.career.recommendation.dto.experience;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * POST /api/v1/users/me/experiences/enrich 응답. 서버는 이 결과를 저장하지 않는다 —
 * FE가 받은 값을 경험에 병합해 기존 PUT /users/me/spec으로 저장한다.
 * Gemini 실패·전역 일일 상한 초과 시에도 200과 함께 빈 값을 준다.
 */
@Getter
@Builder
public class ExperienceEnrichResponse {

    /** ExperienceArea 13종 코드만. Gemini가 반환한 미지 코드는 필터링된다. */
    private List<String> areas;

    /** IMPLEMENTED | CONFIGURED | BOILERPLATE. 검증 실패 시 null. */
    private String depth;

    /** 100자 이내로 컷된 한 줄 요약. 빈 값이면 null. */
    private String roleSummary;

    public static ExperienceEnrichResponse empty() {
        return ExperienceEnrichResponse.builder().areas(List.of()).depth(null).roleSummary(null).build();
    }
}
