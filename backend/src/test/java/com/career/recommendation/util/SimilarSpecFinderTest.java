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
}
