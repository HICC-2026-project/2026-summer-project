package com.career.recommendation.service;

import com.career.recommendation.repository.PasserDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 검증된 합격자 전체의 자격증 풀을 캐싱하여 제공한다.
 *
 * 매 추천 요청마다 PasserData 테이블을 전체 스캔하여 자격증 배열을 합치는 대신,
 * Caffeine 인메모리 캐시로 1시간 동안 결과를 유지한다.
 * 합격자 제보 시 evictCache()를 호출하면 캐시를 즉시 무효화할 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalCertPoolService {

    private final PasserDataRepository passerDataRepository;

    /**
     * 검증된 합격자 전체의 자격증 원본 문자열 풀을 반환한다.
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

    /** 합격자 제보 시 캐시를 즉시 무효화하고 싶다면 이 메서드를 호출한다. */
    @CacheEvict("globalCertPool")
    public void evictCache() {
        log.info("globalCertPool 캐시가 수동으로 무효화되었습니다.");
    }
}
