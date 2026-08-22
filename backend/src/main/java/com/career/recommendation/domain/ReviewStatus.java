package com.career.recommendation.domain;

/** 합격자 제보 검수 상태. passer_data의 is_verified + reviewed_at 두 컬럼에서 파생한다(PasserData.reviewStatus). */
public enum ReviewStatus {
    /** 검수 전. 비교·추천에 쓰이지 않는다. */
    PENDING,
    /** 승인됨. 비교 가능 집합에 포함. */
    VERIFIED,
    /** 반려됨. 데이터는 보존하되 비교에서 제외. */
    REJECTED
}
