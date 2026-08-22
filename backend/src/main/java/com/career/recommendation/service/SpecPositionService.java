package com.career.recommendation.service;

import com.career.recommendation.dto.position.SpecPositionResult;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.util.SpecPositionCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * BE-1 담당 — "직무 프로필 조회(캐시) → 위치·갭 계산"의 단일 진입점.
 *
 * RecommendationService와 RoadmapService가 각자 이 조합(getJobProfile +
 * getOverallProfile 폴백 + calculate)을 따로 들고 있으면, 폴백 규칙이 바뀔 때 한쪽만
 * 고쳐져 추천과 로드맵이 조용히 다른 데이터를 보게 된다 — "두 화면이 같은 갭을 보고
 * 말한다"는 v9의 핵심 약속이 깨진다. 그래서 여기 한 곳으로 모은다.
 *
 * JobSpecProfileService 안에 이 메서드를 두지 않는 이유: 같은 빈 안에서
 * this.getJobProfile()을 부르면 Spring 프록시를 거치지 않아 @Cacheable이 무시된다
 * (self-invocation). 별도 빈에서 호출해야 캐시가 실제로 동작한다.
 *
 * 전체 프로필은 Supplier로 넘긴다 — 직무 프로필이 표본(3명)을 충족하면 폴백을 아예
 * 조회하지 않아, 캐시 미스 시의 전체 테이블 스캔이 필요할 때만 일어난다.
 */
@Service
@RequiredArgsConstructor
public class SpecPositionService {

    private final JobSpecProfileService jobSpecProfileService;
    private final SpecPositionCalculator specPositionCalculator;

    public SpecPositionResult calculate(UserSpec userSpec, String jobType) {
        return specPositionCalculator.calculate(
                userSpec,
                jobSpecProfileService.getJobProfile(jobType),
                jobSpecProfileService::getOverallProfile);
    }
}
