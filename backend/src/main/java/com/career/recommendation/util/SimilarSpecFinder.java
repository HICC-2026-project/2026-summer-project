package com.career.recommendation.util;

import com.career.recommendation.entity.PasserData;
import com.career.recommendation.repository.PasserDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /** 정규화 학점 비율 유사 범위: ±7%p */
    private static final BigDecimal GPA_RATIO_MARGIN = new BigDecimal("0.07");

    /** 유사 합격자 최대 조회 수 */
    private static final int TOP_N = 5;

    private final PasserDataRepository passerDataRepository;

    /**
     * 목표 직무 + 학점 복합 조건으로 유사 합격자 Top 5를 검색한다.
     * 직무 조건으로 0건이면 학점 범위만으로 재시도(폴백)한다.
     *
     * @param jobType 목표 직무 (예: "BACKEND", "FRONTEND", "AI_ML")
     * @param gpa     사용자 학점
     * @param gpaMax  사용자 학점 만점
     * @return 검색 결과 (0~5건)
     */
    public List<PasserData> find(String jobType, BigDecimal gpa, BigDecimal gpaMax) {
        if (gpa == null || gpaMax == null || gpaMax.signum() <= 0) {
            return List.of();
        }

        BigDecimal userRatio = gpa.divide(gpaMax, 4, RoundingMode.HALF_UP);
        BigDecimal minRatio = userRatio.subtract(GPA_RATIO_MARGIN).max(BigDecimal.ZERO);
        BigDecimal maxRatio = userRatio.add(GPA_RATIO_MARGIN).min(BigDecimal.ONE);
        PageRequest pageRequest = PageRequest.of(0, TOP_N);

        // 1차: 직무 + 정규화 학점 비율 복합 검색 (±7%p)
        if (jobType != null && !jobType.isBlank()) {
            List<PasserData> result = passerDataRepository.findSimilarByJobTypeAndGpaRatio(
                    jobType, minRatio, maxRatio, pageRequest);
            if (!result.isEmpty()) {
                return result;
            }
        }

        // 2차 폴백: 해당 직무 범위 내 데이터 부족 → 정규화 학점 비율만으로 재시도 (±7%p)
        List<PasserData> result = passerDataRepository.findSimilarByGpaRatio(
                minRatio, maxRatio, pageRequest);
        if (!result.isEmpty()) {
            return result;
        }

        // 3차 폴백: 고학점/저학점 등 ±7%p 범위 이탈 시 해당 직무 학점 차이 최솟값 순 검색
        if (jobType != null && !jobType.isBlank()) {
            result = passerDataRepository.findClosestByJobTypeAndGpaRatio(
                    jobType, userRatio, pageRequest);
            if (!result.isEmpty()) {
                return result;
            }
        }

        // 4차 폴백: 전체 합격자 데이터 중 학점 차이 최솟값 순 검색
        return passerDataRepository.findClosestByGpaRatio(
                userRatio, pageRequest);
    }

    /**
     * 유사 합격자 목록에 따른 비교 요약 메시지를 반환한다.
     *
     * find()는 목표 직무 + 학점 범위로 못 찾으면 2차(직무 무시, 학점만)·4차(직무 무시,
     * 학점 최근접) 폴백을 탄다. 예전엔 이 메서드가 count만 받아 폴백 여부와 무관하게
     * 항상 "유사 {jobType} 합격자 N명과 비교한 결과입니다"라고 단언했다 — 실제로는 다른
     * 직무 합격자와 비교했는데도 그렇게 말한 것이다. v6(직무별 자격증 가중치) 도입 후엔
     * 이게 문구 수준을 넘어선다: 같은 요청 안에서 자격증 가중치는 목표 직무 기준
     * (jobPasserCertRows)으로 유도되는데, 정작 비교 대상(passers)이 다른 직무면 "그 직무
     * 보유율로 만든 가중치로 다른 직무 합격자를 채점"하는 불일치가 생긴다. 그래서 목록
     * 자체를 받아 실제로 전원이 목표 직무인지 확인한 뒤에만 직무명을 단언한다.
     */
    public String buildComparisonMessage(List<PasserData> passers, String jobType) {
        if (passers == null || passers.isEmpty()) {
            return "아직 비교할 합격자 데이터가 부족해 AI 일반 추천을 제공합니다.";
        }

        int count = passers.size();
        boolean hasJobType = jobType != null && !jobType.isBlank();
        boolean allMatchJob = hasJobType
                && passers.stream().allMatch(p -> jobType.equals(p.getJobType()));

        if (allMatchJob) {
            return String.format("유사 %s 합격자 %d명과 비교한 결과입니다.", jobType, count);
        }
        if (!hasJobType) {
            // find()는 gpa/gpaMax가 없으면 DB를 보지도 않고 빈 리스트를 반환하므로,
            // 이 분기는 "직무가 없어서"가 아니라 이 갈래에 실제로 도달했다면 항상
            // gpa는 있고 jobType만 없는 경우다. "목표 직무 미설정"이 원인이 정확하다.
            return String.format("목표 직무 미설정 상태로, 학점이 비슷한 전체 합격자 %d명과 비교한 결과입니다.", count);
        }
        // 목표 직무는 있지만 그 직무 데이터가 부족해 학점 기준 폴백(2·4차)을 탄 경우.
        return String.format("%s 합격자 데이터가 부족해, 직무 구분 없이 학점이 비슷한 합격자 %d명과 비교한 결과입니다.",
                jobType, count);
    }
}
