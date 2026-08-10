package com.career.recommendation.util;

import com.career.recommendation.entity.TargetJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptDataBuilderTest {

    private final PromptDataBuilder builder = new PromptDataBuilder(new ObjectMapper());

    @Test
    void 목표_직무가_없으면_미설정을_반환한다() {
        assertThat(builder.buildTargetJobString(null)).isEqualTo("미설정");
    }

    @Test
    void companySize_industry가_null이면_문자열_null_대신_미설정으로_대체된다() {
        // companySize·industry는 TargetJobRequest에 @NotBlank가 없는 선택 입력이라
        // null로 저장될 수 있다. null 폴백 없이 이어붙이면 "BACKEND / null / null"이라는
        // 리터럴 문자열이 그대로 Gemini 프롬프트에 들어가던 버그가 있었다.
        TargetJob targetJob = new TargetJob();
        targetJob.setJobType("BACKEND");
        targetJob.setCompanySize(null);
        targetJob.setIndustry(null);

        String result = builder.buildTargetJobString(targetJob);

        assertThat(result).isEqualTo("BACKEND / 미설정 / 미설정");
        assertThat(result).doesNotContain("null");
    }

    @Test
    void companySize_industry가_있으면_그대로_반영된다() {
        TargetJob targetJob = new TargetJob();
        targetJob.setJobType("BACKEND");
        targetJob.setCompanySize("대기업");
        targetJob.setIndustry("IT");

        assertThat(builder.buildTargetJobString(targetJob)).isEqualTo("BACKEND / 대기업 / IT");
    }
}
