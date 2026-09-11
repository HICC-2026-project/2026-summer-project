package com.career.recommendation.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobTypeTest {

    @Test
    void 회의에서_확정한_6종만_존재한다() {
        assertThat(JobType.values())
                .extracting(Enum::name)
                .containsExactly("BACKEND", "FRONTEND", "DATA_ENGINEER", "AI_ML", "PM", "SECURITY");
    }

    @Test
    void 공백과_소문자는_관대하게_받는다() {
        assertThat(JobType.from("  backend ")).contains(JobType.BACKEND);
        assertThat(JobType.of("ai_ml")).isEqualTo(JobType.AI_ML);
    }

    @Test
    void 레거시_별칭과_한글_라벨은_코드로_인정하지_않는다() {
        // V13이 DB의 BE/FE/보안 등을 이미 정규화했으므로 API에서 다시 받을 이유가 없다.
        assertThat(JobType.from("BE")).isEmpty();
        assertThat(JobType.from("AI/ML")).isEmpty();
        assertThat(JobType.from("백엔드")).isEmpty();
        assertThat(JobType.from(null)).isEmpty();
        assertThat(JobType.from("   ")).isEmpty();
        assertThatThrownBy(() -> JobType.of("BE")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 코드를_라벨로_바꾼다() {
        assertThat(JobType.labelOf("SECURITY")).isEqualTo("보안");
    }

    @Test
    void 알_수_없는_코드의_라벨은_원문을_돌려줘서_문구가_깨지지_않는다() {
        assertThat(JobType.labelOf("UNKNOWN")).isEqualTo("UNKNOWN");
        assertThat(JobType.labelOf(null)).isNull();
    }
}
