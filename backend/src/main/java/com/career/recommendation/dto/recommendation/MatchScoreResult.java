package com.career.recommendation.dto.recommendation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchScoreResult {
    private int totalScore;
    private List<CompareRowDto> compareRows;

    /** 사용자가 입력했지만 어느 자격증 인식 층에서도 매칭되지 않은 원본 표기 목록. */
    @Builder.Default
    private List<String> unrecognizedCertifications = List.of();
}
