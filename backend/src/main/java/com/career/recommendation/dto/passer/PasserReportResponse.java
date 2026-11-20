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

    /**
     * 함수 이름: pending()
     * 기능: 합격자 제보 접수 완료 응답을 만든다(제보 ID, 검수 상태, 검수 전에는 추천 데이터로 쓰이지 않는다는 안내 메시지).
     * 파라미터: PasserData passerData -> 접수된 합격자 제보 엔티티
     * 반환값: PasserReportResponse -> 제보 ID·상태·안내 메시지를 담은 응답 DTO
     */
    public static PasserReportResponse pending(PasserData passerData) {
        return PasserReportResponse.builder()
                .reportId(passerData.getId())
                .status(passerData.reviewStatus().name())
                .message("합격자 제보가 접수되었습니다. 검수 전에는 추천 데이터로 사용되지 않습니다.")
                .build();
    }
}
