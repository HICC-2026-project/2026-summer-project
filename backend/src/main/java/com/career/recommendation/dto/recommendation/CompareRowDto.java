package com.career.recommendation.dto.recommendation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompareRowDto {
    private String label;
    private String weight;
    private String myVal;
    private String avgVal;
    private int myPct;
    private int avgPct;
    private String status; // "충족" | "부족"
}
