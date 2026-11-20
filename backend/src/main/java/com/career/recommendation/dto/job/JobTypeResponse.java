package com.career.recommendation.dto.job;

import com.career.recommendation.domain.JobType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JobTypeResponse {
    private final String code;
    private final String label;

    /**
     * 함수 이름: from()
     * 기능: JobType enum의 이름(code)과 한글 라벨(label)을 담은 응답 DTO를 만든다.
     * 파라미터: JobType type -> 변환할 직무 유형 enum
     * 반환값: JobTypeResponse -> code(enum 이름)와 label을 가진 응답 DTO
     */
    public static JobTypeResponse from(JobType type) {
        return new JobTypeResponse(type.name(), type.getLabel());
    }
}
