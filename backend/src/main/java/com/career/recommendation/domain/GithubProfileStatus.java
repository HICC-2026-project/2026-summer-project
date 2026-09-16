package com.career.recommendation.domain;

/**
 * E3(1단계) GitHub 공개 레포 분석 상태. github_profiles.status에 저장되는 값의 단일 정의.
 */
public enum GithubProfileStatus {
    /** 분석 요청 접수 — 비동기 작업이 아직 끝나지 않음. 동시 요청은 이 상태에서 409로 막는다. */
    PENDING,
    /** 분석 완료. job_ratios·repos·commit_total·active_months·analyzed_at이 채워져 있다. */
    DONE,
    /** 분석 실패 — failure_reason에 사용자 노출용 한국어 사유. */
    FAILED,
    /** 레이트리밋으로 중단 — 그때까지 모은 부분 결과만 저장. */
    RATE_LIMITED
}
