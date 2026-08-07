package com.career.recommendation.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasserDataTest {

    @Test
    void 출처를_지정하지_않으면_UNKNOWN을_사용한다() {
        PasserData passerData = PasserData.builder().build();

        assertThat(passerData.getDataOrigin()).isEqualTo("UNKNOWN");
    }

    @Test
    void 합성_데이터는_DEMO_출처로_구분할_수_있다() {
        PasserData passerData = PasserData.builder()
                .dataOrigin("DEMO")
                .build();

        assertThat(passerData.getDataOrigin()).isEqualTo("DEMO");
    }
}
