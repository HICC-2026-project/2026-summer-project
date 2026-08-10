package com.career.recommendation.service;

import com.career.recommendation.config.CacheConfig;
import com.career.recommendation.repository.PasserDataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GlobalCertPoolService의 @Cacheable 애노테이션이 실제 Spring 캐시 프록시를 통과할 때
 * 크래시하지 않는지 검증한다.
 *
 * Mockito @Mock으로만 테스트하면(RecommendationServiceFallbackTest처럼) 캐시 AOP 프록시가
 * 전혀 개입하지 않아 이 클래스의 캐시 애노테이션 자체가 잘못돼도(SpEL 오타, key/condition
 * 혼동 등) 통과해버린다. 실제로 getJobPasserCertRows(null)이 프로덕션에서 500을 내는 버그가
 * 그렇게 숨어 있었다 — unless를 condition 자리에 써서, jobType이 null일 때 캐시 키 계산
 * 단계(Assert.notNull)에서 메서드 본문의 null 가드보다 먼저 IllegalArgumentException이
 * 터졌다. 이 테스트는 @EnableCaching + 실제 CacheConfig로 그 프록시를 띄워 재발을 막는다.
 * DB는 필요 없어 PasserDataRepository만 Mockito로 대체한다(전체 Spring Boot 컨텍스트·DB
 * 연결 없이 캐시 AOP만 검증).
 */
class GlobalCertPoolServiceCacheTest {

    @Configuration
    @EnableCaching
    static class TestConfig extends CacheConfig {
        @Bean
        PasserDataRepository passerDataRepository() {
            PasserDataRepository mock = mock(PasserDataRepository.class);
            when(mock.findCertificationArraysByJobType(any())).thenReturn(List.of());
            when(mock.findAllVerifiedCertificationArrays()).thenReturn(List.of());
            return mock;
        }

        @Bean
        GlobalCertPoolService globalCertPoolService(PasserDataRepository repo) {
            return new GlobalCertPoolService(repo);
        }
    }

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    @Test
    void 목표_직무가_null인_유저도_예외_없이_빈_목록을_받는다() {
        // TargetJob을 아직 설정하지 않은 신규 가입 직후 유저의 정상적인 상태다.
        // 캐시 프록시가 실제로 개입하는 컨텍스트가 아니면 이 경로의 크래시를 잡을 수 없다.
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        GlobalCertPoolService service = context.getBean(GlobalCertPoolService.class);

        assertThatCode(() -> {
            List<String[]> result = service.getJobPasserCertRows(null);
            org.assertj.core.api.Assertions.assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    void 목표_직무가_공백_문자열이어도_예외_없이_빈_목록을_받는다() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        GlobalCertPoolService service = context.getBean(GlobalCertPoolService.class);

        assertThatCode(() -> {
            List<String[]> result = service.getJobPasserCertRows("  ");
            org.assertj.core.api.Assertions.assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    void 정상_직무_코드는_캐시_프록시를_통과해_정상_동작한다() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        GlobalCertPoolService service = context.getBean(GlobalCertPoolService.class);

        assertThatCode(() -> service.getJobPasserCertRows("BACKEND")).doesNotThrowAnyException();
    }
}
