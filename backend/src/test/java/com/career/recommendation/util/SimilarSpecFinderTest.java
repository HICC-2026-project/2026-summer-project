package com.career.recommendation.util;

import com.career.recommendation.entity.PasserData;
import com.career.recommendation.repository.PasserDataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimilarSpecFinderTest {

    @Mock
    private PasserDataRepository passerDataRepository;

    @Test
    void 직무와_정규화_학점비율로_유사_합격자를_조회한다() {
        PasserData expected = PasserData.builder().build();
        when(passerDataRepository.findSimilarByJobTypeAndGpaRatio(
                eq("BACKEND"), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(expected));
        SimilarSpecFinder finder = new SimilarSpecFinder(passerDataRepository);

        List<PasserData> result = finder.find(
                "BACKEND", new BigDecimal("3.60"), new BigDecimal("4.50"));

        assertThat(result).containsExactly(expected);
        ArgumentCaptor<BigDecimal> minCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> maxCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(passerDataRepository).findSimilarByJobTypeAndGpaRatio(
                eq("BACKEND"), minCaptor.capture(), maxCaptor.capture(), any(Pageable.class));
        assertThat(minCaptor.getValue()).isEqualByComparingTo("0.73");
        assertThat(maxCaptor.getValue()).isEqualByComparingTo("0.87");
        verify(passerDataRepository, never()).findSimilarByGpaRatio(any(), any(), any());
    }

    @Test
    void 동일_직무_결과가_없으면_학점비율만으로_다시_조회한다() {
        PasserData expected = PasserData.builder().build();
        when(passerDataRepository.findSimilarByJobTypeAndGpaRatio(
                eq("BACKEND"), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());
        when(passerDataRepository.findSimilarByGpaRatio(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(expected));
        SimilarSpecFinder finder = new SimilarSpecFinder(passerDataRepository);

        assertThat(finder.find("BACKEND", new BigDecimal("3.60"), new BigDecimal("4.50")))
                .containsExactly(expected);
    }

    @Test
    void 학점이나_만점이_없으면_DB를_조회하지_않는다() {
        SimilarSpecFinder finder = new SimilarSpecFinder(passerDataRepository);

        assertThat(finder.find("BACKEND", new BigDecimal("3.60"), null)).isEmpty();
        verifyNoInteractions(passerDataRepository);
    }

    @Test
    void 학점_범위_내_결과가_없으면_가장_가까운_학점_비율로_다시_조회한다() {
        PasserData expected = PasserData.builder().build();
        when(passerDataRepository.findSimilarByJobTypeAndGpaRatio(eq("BACKEND"), any(), any(), any()))
                .thenReturn(List.of());
        when(passerDataRepository.findSimilarByGpaRatio(any(), any(), any()))
                .thenReturn(List.of());
        when(passerDataRepository.findClosestByJobTypeAndGpaRatio(eq("BACKEND"), any(), any()))
                .thenReturn(List.of(expected));

        SimilarSpecFinder finder = new SimilarSpecFinder(passerDataRepository);

        List<PasserData> result = finder.find("BACKEND", new BigDecimal("4.50"), new BigDecimal("4.50"));

        assertThat(result).containsExactly(expected);
        verify(passerDataRepository).findClosestByJobTypeAndGpaRatio(eq("BACKEND"), eq(new BigDecimal("1.0000")), any());
    }

    // --- 비교 문구 (buildComparisonMessage) ---

    @Test
    void 결과가_비어있으면_데이터_부족_문구를_반환한다() {
        SimilarSpecFinder finder = new SimilarSpecFinder(passerDataRepository);

        assertThat(finder.buildComparisonMessage(List.of(), "BACKEND"))
                .isEqualTo("아직 비교할 합격자 데이터가 부족해 AI 일반 추천을 제공합니다.");
        assertThat(finder.buildComparisonMessage(null, "BACKEND"))
                .isEqualTo("아직 비교할 합격자 데이터가 부족해 AI 일반 추천을 제공합니다.");
    }

    @Test
    void 결과_전원이_목표_직무면_직무명을_단언한다() {
        SimilarSpecFinder finder = new SimilarSpecFinder(passerDataRepository);
        List<PasserData> passers = List.of(
                PasserData.builder().jobType("BACKEND").build(),
                PasserData.builder().jobType("BACKEND").build());

        assertThat(finder.buildComparisonMessage(passers, "BACKEND"))
                .isEqualTo("유사 BACKEND 합격자 2명과 비교한 결과입니다.");
    }

    @Test
    void 목표_직무가_없으면_직무_미설정_문구를_반환한다() {
        // find()는 gpa/gpaMax가 없으면 DB를 보지도 않고 빈 리스트를 주므로, 이 분기에
        // 실제로 도달했다면 원인은 "직무 데이터 부족"이 아니라 "목표 직무 미설정"이다.
        SimilarSpecFinder finder = new SimilarSpecFinder(passerDataRepository);
        List<PasserData> passers = List.of(PasserData.builder().jobType("BACKEND").build());

        assertThat(finder.buildComparisonMessage(passers, null))
                .isEqualTo("목표 직무 미설정 상태로, 학점이 비슷한 전체 합격자 1명과 비교한 결과입니다.");
        assertThat(finder.buildComparisonMessage(passers, ""))
                .isEqualTo("목표 직무 미설정 상태로, 학점이 비슷한 전체 합격자 1명과 비교한 결과입니다.");
    }

    @Test
    void 목표_직무는_있지만_결과에_다른_직무가_섞이면_폴백_문구를_반환한다() {
        // find()의 2차·4차 폴백(직무 무시, 학점만/최근접)이 실제로 발동한 상황을 재현한다.
        // 예전엔 이 경우에도 무조건 "유사 BACKEND 합격자 N명과 비교한 결과입니다"라고
        // 단언해, 실제로는 다른 직무와 비교했는데도 그렇게 말하는 버그가 있었다.
        SimilarSpecFinder finder = new SimilarSpecFinder(passerDataRepository);
        List<PasserData> passers = List.of(
                PasserData.builder().jobType("FRONTEND").build(),
                PasserData.builder().jobType("PM").build());

        assertThat(finder.buildComparisonMessage(passers, "BACKEND"))
                .isEqualTo("BACKEND 합격자 데이터가 부족해, 직무 구분 없이 학점이 비슷한 합격자 2명과 비교한 결과입니다.");
    }

    @Test
    void 결과에_목표_직무와_다른_직무가_한_명이라도_섞이면_직무명을_단언하지_않는다() {
        // 전원이 아니라 한 명만 섞여도 "유사 {jobType} 합격자"라고 말하면 안 된다.
        SimilarSpecFinder finder = new SimilarSpecFinder(passerDataRepository);
        List<PasserData> passers = List.of(
                PasserData.builder().jobType("BACKEND").build(),
                PasserData.builder().jobType("FRONTEND").build());

        assertThat(finder.buildComparisonMessage(passers, "BACKEND"))
                .doesNotContain("유사 BACKEND 합격자");
    }
}
