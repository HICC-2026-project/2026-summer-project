package com.career.recommendation.dto.admin;

import com.career.recommendation.entity.PasserData;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AdminPasserEntryResponse {

    private UUID passerId;
    private String status;
    private String message;

    public static AdminPasserEntryResponse created(PasserData passerData) {
        return AdminPasserEntryResponse.builder()
                .passerId(passerData.getId())
                .status(passerData.reviewStatus().name())
                .message("합격자 데이터가 등록되었습니다. 검수 없이 즉시 비교 데이터로 반영됩니다.")
                .build();
    }
}
