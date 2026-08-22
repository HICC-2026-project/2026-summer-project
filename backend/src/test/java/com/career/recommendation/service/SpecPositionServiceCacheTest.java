package com.career.recommendation.service;

import com.career.recommendation.config.CacheConfig;
import com.career.recommendation.dto.position.SpecPositionResult;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.repository.PasserDataRepository;
import com.career.recommendation.util.JobSpecProfileBuilder;
import com.career.recommendation.util.SpecPositionCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SpecPositionService가 JobSpecProfileService를 "다른 빈에서" 호출하므로 @Cacheable이 실제로
 * 동작하는지 검증한다 — 같은 빈 안에서 this.getJobProfile()을 부르면 프록시를 거치지 않아
 * 캐시가 조용히 무시되는데(self-invocation), 그 회귀를 DB 없이 잡기 위한 슬라이스 테스트.
 *
 * 실제 CacheConfig(Caffeine)와 @EnableCaching을 올리고 리포지토리만 목으로 둔다.
 */
@SpringJUnitConfig(SpecPositionServiceCacheTest.Config.class)
class SpecPositionServiceCacheTest {

    @Configuration
    @EnableCaching
    @Import({CacheConfig.class, JobSpecProfileService.class, SpecPositionService.class,
            JobSpecProfileBuilder.class, SpecPositionCalculator.class})
    static class Config {
    }

    @Autowired
    private SpecPositionService specPositionService;
    @Autowired
    private JobSpecProfileService jobSpecProfileService;
    @Autowired
    private CacheManager cacheManager;
    @MockBean
    private PasserDataRepository passerDataRepository;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void 같은_직무를_두_번_계산하면_DB_집계는_한_번만_일어난다() {
        when(passerDataRepository.findAllComparableByJobType("BACKEND")).thenReturn(threePassers());

        specPositionService.calculate(user(), "BACKEND");
        specPositionService.calculate(user(), "BACKEND");

        verify(passerDataRepository, times(1)).findAllComparableByJobType("BACKEND");
    }

    @Test
    void 직무_표본이_충분하면_전체_프로필은_조회조차_하지_않는다() {
        // Supplier로 넘기는 이유 — 폴백이 필요할 때만 전체 테이블 스캔이 일어나야 한다.
        when(passerDataRepository.findAllComparableByJobType("BACKEND")).thenReturn(threePassers());

        SpecPositionResult result = specPositionService.calculate(user(), "BACKEND");

        assertThat(result.getBasis()).isEqualTo("JOB");
        verify(passerDataRepository, never()).findAllComparable();
    }

    @Test
    void 직무_표본이_부족하면_전체_프로필로_폴백하고_그것도_캐시된다() {
        when(passerDataRepository.findAllComparableByJobType("PM")).thenReturn(List.of());
        when(passerDataRepository.findAllComparable()).thenReturn(threePassers());

        SpecPositionResult first = specPositionService.calculate(user(), "PM");
        specPositionService.calculate(user(), "PM");

        assertThat(first.getBasis()).isEqualTo("OVERALL");
        verify(passerDataRepository, times(1)).findAllComparable();
    }

    @Test
    void 직무_미설정_null은_캐시_키_예외_없이_전체_폴백을_탄다() {
        // @Cacheable key="#jobType"에 null이 들어가면 condition 없이는 IllegalArgumentException.
        // 목표 직무를 아직 고르지 않은 사용자 전원이 500을 맞는 경로라 회귀를 막는다.
        when(passerDataRepository.findAllComparable()).thenReturn(threePassers());

        SpecPositionResult result = specPositionService.calculate(user(), null);

        assertThat(result.getBasis()).isEqualTo("OVERALL");
        assertThat(result.getBasisMessage()).contains("목표 직무 미설정");
        verify(passerDataRepository, never()).findAllComparableByJobType(anyString());
    }

    @Test
    void evictAll_후에는_다시_DB에서_집계한다() {
        when(passerDataRepository.findAllComparableByJobType("BACKEND")).thenReturn(threePassers());

        specPositionService.calculate(user(), "BACKEND");
        jobSpecProfileService.evictAll();
        specPositionService.calculate(user(), "BACKEND");

        verify(passerDataRepository, times(2)).findAllComparableByJobType("BACKEND");
    }

    private List<PasserData> threePassers() {
        return List.of(passer("3.50", 800), passer("3.80", 850), passer("4.00", 900));
    }

    private PasserData passer(String gpa, int toeic) {
        return PasserData.builder()
                .gpa(new BigDecimal(gpa)).gpaMax(new BigDecimal("4.50"))
                .languageScores(List.of(Map.of("type", "TOEIC", "score", toeic)))
                .certifications(new String[0])
                .build();
    }

    private UserSpec user() {
        return UserSpec.builder()
                .gpa(new BigDecimal("3.80")).gpaMax(new BigDecimal("4.50"))
                .languageScores(List.of(Map.of("type", "TOEIC", "score", 850)))
                .certifications(new String[0])
                .build();
    }
}
