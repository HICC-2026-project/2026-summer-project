package com.career.recommendation.dto.admin;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/** 관리자 운영 요약 — 데이터가 쌓이는지, Gemini가 살아 있는지 한 화면에서 본다. */
@Getter
@Builder
public class OpsSummaryResponse {

    /** 검수 대기 제보 수. 0이 아니면 검수 화면으로. */
    private long pendingReports;

    /** 직무 코드 → 비교 가능 합격자 수. MIN_SAMPLE(3) 미만인 직무는 비교가 전체 폴백을 탄다. */
    private Map<String, Long> comparablePassersByJob;

    private int minSampleSize;

    private Gemini gemini;

    @Getter
    @Builder
    public static class Gemini {
        /** 오늘(KST) 시도 수 / 일일 상한. */
        private int usedToday;
        private int dailyLimit;
        /** 기동 이후 누적. */
        private long success;
        private long failure;
        private double failureRate;
        private long avgLatencyMs;
        private long maxLatencyMs;
        private Long lastFailureEpochMs;
    }
}
