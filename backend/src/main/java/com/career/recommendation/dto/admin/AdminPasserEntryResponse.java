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

    /**
     * 함수 이름: created()
     * 기능: 관리자가 수기 등록한 합격자 데이터로 등록 완료 응답을 만든다(ID, 검수 상태, 안내 메시지).
     * 파라미터: PasserData passerData -> 등록된 합격자 엔티티
     * 반환값: AdminPasserEntryResponse -> 합격자 ID·상태·안내 메시지를 담은 응답 DTO
     */
    public static AdminPasserEntryResponse created(PasserData passerData) {
        return AdminPasserEntryResponse.builder()
                .passerId(passerData.getId())
                .status(passerData.reviewStatus().name())
                .message("합격자 데이터가 등록되었습니다. 검수 없이 즉시 비교 데이터로 반영됩니다.")
                .build();
    }
}
