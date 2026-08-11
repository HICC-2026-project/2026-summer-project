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

    /**
     * 인식된 자격증 개수 (canonical 기준 dedup 후, 비교 탭 자격증 행과 같은 집계).
     * FE 홈 카드가 이 값을 그대로 써야 비교 탭과 숫자가 항상 일치한다 —
     * "입력 개수 − 미인식 개수" 역산은 canonical 접힘 때문에 틀린다(계산부 주석 참고).
     */
    @Builder.Default
    private int recognizedCertificationCount = 0;
}
