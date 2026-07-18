package com.career.recommendation.util;

import com.career.recommendation.entity.PasserData;
import com.career.recommendation.repository.PasserDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * BE-1 담당 — 합격자 DB에서 유사 스펙 케이스를 검색하는 컴포넌트.
 * MatchScoreCalculator와 역할을 분리한다:
 *   - SimilarSpecFinder  : 합격자 후보를 "검색"
 *   - MatchScoreCalculator: 검색된 후보와 유저 스펙을 "비교·점수화"
 */
@Component
@RequiredArgsConstructor
public class SimilarSpecFinder {

    /** 학점 유사 범위: ±0.3 */
    private static final BigDecimal GPA_MARGIN = new BigDecimal("0.3");

    /** 유사 합격자 최대 조회 수 */
    private static final int TOP_N = 5;

    private final PasserDataRepository passerDataRepository;

    /**
     * 목표 직무 + 학점 복합 조건으로 유사 합격자 Top 5를 검색한다.
     * 직무 조건으로 0건이면 학점 범위만으로 재시도(폴백)한다.
     *
     * @param jobType 목표 직무 (예: "BE", "FE", "AI/ML")
     * @param gpa     사용자 학점
     * @return 검색 결과 (0~5건)
     */
    public List<PasserData> find(String jobType, BigDecimal gpa) {
        if (gpa == null) {
            return List.of();
        }
        BigDecimal minGpa = gpa.subtract(GPA_MARGIN);
        BigDecimal maxGpa = gpa.add(GPA_MARGIN);

        // 1차: 직무 + 학점 복합 검색
        if (jobType != null && !jobType.isBlank()) {
            List<PasserData> result = passerDataRepository.findSimilarByJobTypeAndGpa(
                    jobType, minGpa, maxGpa, PageRequest.of(0, TOP_N));
            if (!result.isEmpty()) {
                return result;
            }
        }

        // 2차 폴백: 해당 직무 데이터 부족 → 학점 범위만으로 재시도
        return passerDataRepository.findSimilarByGpa(minGpa, maxGpa)
                .stream()
                .limit(TOP_N)
                .toList();
    }

    /**
     * 유사 합격자 수에 따른 비교 요약 메시지를 반환한다.
     */
    public String buildComparisonMessage(int count, String jobType) {
        if (count == 0) {
            return "아직 이 활동은 데이터가 부족해 AI 일반 추천을 제공합니다.";
        }
        String jobLabel = (jobType != null && !jobType.isBlank()) ? jobType + " " : "";
        return String.format("유사 %s합격자 %d명과 비교한 결과입니다.", jobLabel, count);
    }
}
