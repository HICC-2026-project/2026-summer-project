package com.career.recommendation.service;

import com.career.recommendation.repository.PasserDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 검증된 합격자 전체의 자격증 풀 및 직무별 합격자 자격증 원본을 캐싱하여 제공한다.
 *
 * 매 추천 요청마다 PasserData 테이블을 전체(또는 직무별) 스캔하는 대신,
 * Caffeine 인메모리 캐시로 1시간 동안 결과를 유지한다.
 * 합격자 제보 시 evictAll()을 호출하면 두 캐시를 즉시 무효화할 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalCertPoolService {

    private final PasserDataRepository passerDataRepository;

    /**
     * 검증된 합격자 전체의 자격증 원본 문자열 풀을 반환한다(2층 인식용).
     * 1시간 캐시 — 합격자 제보가 들어와도 최대 1시간 후 반영되므로 실시간성은
     * 포기하지만, 매 추천 요청마다 전체 스캔하는 부하를 제거한다.
     */
    @Cacheable("globalCertPool")
    @Transactional(readOnly = true)
    public Set<String> getGlobalCertPool() {
        log.info("globalCertPool 캐시 미스 — DB에서 새로 로드합니다.");
        return passerDataRepository.findAllVerifiedCertificationArrays().stream()
                .filter(Objects::nonNull)
                .flatMap(Arrays::stream)
                .collect(Collectors.toSet());
    }

    /**
     * 목표 직무 합격자 전원의 자격증 원본(1인 1행)을 반환한다.
     * MatchScoreCalculator가 자격증 가중치를 이 직무의 보유율에서 유도하는 데 쓴다.
     * jobType별로 캐시 키가 분리되므로 직무마다 독립적으로 1시간 유지된다.
     *
     * @param jobType null·공백이면 빈 목록을 반환한다(호출부가 CERT_WEIGHTS 폴백을 타게 됨).
     *                빈 문자열을 캐시 키로 쓰면 불필요한 캐시 슬롯이 생기므로 캐시 진입 전에 막는다.
     */
    @Cacheable(value = "jobCertRows", key = "#jobType", unless = "#jobType == null || #jobType.isBlank()")
    @Transactional(readOnly = true)
    public List<String[]> getJobPasserCertRows(String jobType) {
        if (jobType == null || jobType.isBlank()) {
            return List.of();
        }
        log.info("jobCertRows 캐시 미스({}) — DB에서 새로 로드합니다.", jobType);
        return passerDataRepository.findCertificationArraysByJobType(jobType);
    }

    /** 합격자 제보 시 두 캐시를 즉시 무효화하고 싶다면 이 메서드를 호출한다. */
    @Caching(evict = {
            @CacheEvict("globalCertPool"),
            @CacheEvict(value = "jobCertRows", allEntries = true)
    })
    public void evictAll() {
        log.info("자격증 캐시(globalCertPool, jobCertRows)가 수동으로 무효화되었습니다.");
    }
}
