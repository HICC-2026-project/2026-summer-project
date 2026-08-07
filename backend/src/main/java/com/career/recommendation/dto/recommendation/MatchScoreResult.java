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
}
