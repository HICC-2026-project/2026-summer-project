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

    @Test
    void 관리자_수기_등록은_ADMIN_ENTRY_출처와_출처메모를_가질_수_있다() {
        PasserData passerData = PasserData.builder()
                .dataOrigin(PasserData.ORIGIN_ADMIN_ENTRY)
                .sourceNote("https://blog.example.com/passed-2026")
                .isVerified(true)
                .build();

        assertThat(passerData.getDataOrigin()).isEqualTo("ADMIN_ENTRY");
        assertThat(passerData.getSourceNote()).isEqualTo("https://blog.example.com/passed-2026");
        assertThat(passerData.reviewStatus().name()).isEqualTo("VERIFIED");
    }
}
