package com.career.recommendation.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 애플리케이션 캐시 설정.
 * globalCertPool 등 변경 빈도가 낮은 데이터를 인메모리에 캐싱하여
 * 매 요청마다 DB 전체 스캔을 수행하는 부하를 제거한다.
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        // jobCertRows: 목표 직무별 합격자 자격증 원본(자격증 가중치 유도용). 직무 코드 수(6개)보다
        // 넉넉하게 잡아 캐시 미스로 인한 재조회가 생기지 않게 한다.
        CaffeineCacheManager manager = new CaffeineCacheManager("globalCertPool", "jobCertRows");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))   // 1시간마다 자동 갱신
                .maximumSize(20));
        return manager;
    }
}
