package com.career.recommendation.service;

import com.career.recommendation.dto.position.JobSpecProfile;
import com.career.recommendation.repository.PasserDataRepository;
import com.career.recommendation.util.JobSpecProfileBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BE-1 담당 — 직무별 요구 프로필(JobSpecProfile)을 집계·캐싱하여 제공한다.
 *
 * 프로필은 합격자 데이터가 바뀔 때만 변하는 집계값이므로 GlobalCertPoolService와 같은
 * 전략으로 Caffeine 1시간 캐시를 쓴다. 합격자 제보 반영 시 evictAll()로 즉시 무효화한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobSpecProfileService {

    private final PasserDataRepository passerDataRepository;
    private final JobSpecProfileBuilder profileBuilder;

    /**
     * 목표 직무의 요구 프로필을 반환한다.
     *
     * ⚠️ condition을 unless로 쓰면 안 된다 — 캐시 키(#jobType)는 메서드 실행 전에 계산되고,
     * jobType이 null이면 그 시점에 Spring이 IllegalArgumentException을 던진다(목표 직무
     * 미설정 유저 전원이 500을 맞는 경로 — GlobalCertPoolService의 동일 주석 참고).
     * condition은 캐시 접근 자체를 막으므로 안전하다.
     */
    @Cacheable(value = "jobSpecProfile", key = "#jobType", condition = "#jobType != null && !#jobType.isBlank()")
    @Transactional(readOnly = true)
    public JobSpecProfile getJobProfile(String jobType) {
        if (jobType == null || jobType.isBlank()) {
            // 직무 미설정이면 빈 프로필 — 호출부(SpecPositionCalculator)가 표본 미달로 보고
            // 전체 프로필 폴백을 탄다.
            return profileBuilder.build(null, java.util.List.of());
        }
        log.info("jobSpecProfile 캐시 미스({}) — DB에서 새로 집계합니다.", jobType);
        return profileBuilder.build(jobType, passerDataRepository.findAllComparableByJobType(jobType));
    }

    /** 직무 구분 없는 전체 합격자 프로필. 직무 표본이 부족할 때의 폴백. */
    @Cacheable("overallSpecProfile")
    @Transactional(readOnly = true)
    public JobSpecProfile getOverallProfile() {
        log.info("overallSpecProfile 캐시 미스 — DB에서 새로 집계합니다.");
        return profileBuilder.build(null, passerDataRepository.findAllComparable());
    }

    /** 합격자 데이터 변경(제보 승인 등) 시 프로필 캐시를 즉시 무효화한다. */
    @Caching(evict = {
            @CacheEvict(value = "jobSpecProfile", allEntries = true),
            @CacheEvict("overallSpecProfile")
    })
    public void evictAll() {
        log.info("직무 프로필 캐시(jobSpecProfile, overallSpecProfile)가 수동으로 무효화되었습니다.");
    }
}
