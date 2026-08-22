package com.career.recommendation.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpecNormalizerTest {

    @Test
    void 부가어와_구분기호를_제거하고_대문자로_정규화한다() {
        assertThat(SpecNormalizer.canonicalCert("정보처리기사 필기")).isEqualTo("정보처리기사");
        assertThat(SpecNormalizer.canonicalCert("정보처리기사(실기)")).isEqualTo("정보처리기사");
        assertThat(SpecNormalizer.canonicalCert("sqld")).isEqualTo("SQLD");
    }

    @Test
    void 전각공백도_제거된다() {
        // U+3000은 \s에 안 잡혀서 놓치기 쉽다 — 놓치면 "SQLD　필기"가 조용히 별개 표기가 된다.
        assertThat(SpecNormalizer.canonicalCert("SQLD　필기")).isEqualTo("SQLD");
    }

    @Test
    void 별칭은_표준_표기로_모인다() {
        assertThat(SpecNormalizer.canonicalCert("SQL개발자")).isEqualTo("SQLD");
        assertThat(SpecNormalizer.canonicalCert("데이터분석준전문가")).isEqualTo("ADSP");
        assertThat(SpecNormalizer.canonicalCert("AWS Solutions Architect Associate")).isEqualTo("AWSSAA");
    }

    @Test
    void 정규화_집합은_변형_표기를_중복_제거한다() {
        assertThat(SpecNormalizer.canonicalCerts(new String[]{"SQLD", "SQLD 필기", "SQL개발자", "", null}))
                .containsExactly("SQLD");
    }

    @Test
    void 어학은_토익_환산_최고점을_쓴다() {
        List<Map<String, Object>> scores = List.of(
                Map.of("type", "TOEIC", "score", 800),
                Map.of("type", "OPIC", "grade", "IH")); // 900 환산

        assertThat(SpecNormalizer.maxEquivalentToeic(scores)).isEqualTo(900.0);
    }

    @Test
    void OPIc_IM_세부등급_미기재도_환산된다() {
        assertThat(SpecNormalizer.maxEquivalentToeic(List.of(Map.of("type", "OPIC", "grade", "IM"))))
                .isEqualTo(750.0); // IM2와 동일
    }

    @Test
    void 어학_미보유는_0이다() {
        assertThat(SpecNormalizer.maxEquivalentToeic(null)).isZero();
        assertThat(SpecNormalizer.maxEquivalentToeic(List.of())).isZero();
    }
}
