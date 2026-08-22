package com.career.recommendation.util;

import java.time.ZoneId;

/**
 * 서비스 기준 시간대. D-day 자정 박제(v8), 마감 자동 비활성화, 하루 갱신 한도 등 "오늘"을 판단하는
 * 모든 곳이 같은 시간대를 써야 한다 — 예전엔 이 상수가 서비스마다 따로 선언돼 있었다.
 */
public final class ServiceTime {

    /** @Scheduled(zone=...)처럼 문자열이 필요한 자리용. */
    public static final String ZONE = "Asia/Seoul";
    public static final ZoneId ZONE_ID = ZoneId.of(ZONE);

    private ServiceTime() {
    }
}
