package com.career.recommendation.dto.passer;

import com.career.recommendation.entity.PasserData;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class PasserReportResponse {

    private UUID reportId;
    private String status;
    private String message;

    public static PasserReportResponse pending(PasserData passerData) {
        return PasserReportResponse.builder()
                .reportId(passerData.getId())
                .status("PENDING")
                .message("합격자 제보가 접수되었습니다. 검수 전에는 추천 데이터로 사용되지 않습니다.")
                .build();
    }
}
