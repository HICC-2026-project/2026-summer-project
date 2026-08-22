package com.career.recommendation.service;

import com.career.recommendation.util.ServiceTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gemini 호출의 서비스 전역 일일 상한.
 *
 * 사용자별 dailyUpdateCount(3회)는 "성공 저장"만 세서 Gemini가 장애로 폴백만 반복하면 증가하지 않는다
 * (RecommendationService 주석 참고). 그 상태에서 사용자가 새로고침을 반복하면 호출 수에 상한이 없었다.
 * 이 가드는 성공·실패와 무관하게 "시도"를 세고, 하루 상한에 닿으면 호출 자체를 건너뛰어 폴백으로
 * 보낸다 — 비용 폭주와 429 연쇄를 끊는 마지막 안전장치다.
 *
 * 단일 인스턴스 메모리 카운터다(현재 EC2 1대). 다중 인스턴스가 되면 Redis 등 공유 카운터로 바꿔야 한다.
 * 날짜 경계는 서비스 시간대(KST) 기준.
 */
@Slf4j
@Component
public class GeminiDailyQuota {

    private final int dailyLimit;
    private final AtomicReference<LocalDate> day = new AtomicReference<>();
    private final AtomicInteger used = new AtomicInteger();

    public GeminiDailyQuota(@Value("${gemini.api.daily-limit:500}") int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    /** 호출 허용 여부. 허용되면 오늘 사용량을 1 올린다. 0 이하 설정은 "상한 없음". */
    public boolean tryAcquire() {
        if (dailyLimit <= 0) {
            return true;
        }
        rollDayIfNeeded(LocalDate.now(ServiceTime.ZONE_ID));
        int next = used.incrementAndGet();
        if (next > dailyLimit) {
            used.decrementAndGet();
            if (next == dailyLimit + 1) {
                log.warn("Gemini 일일 호출 상한({})에 도달 — 이후 요청은 폴백 추천으로 처리합니다.", dailyLimit);
            }
            return false;
        }
        return true;
    }

    public int usedToday() {
        rollDayIfNeeded(LocalDate.now(ServiceTime.ZONE_ID));
        return used.get();
    }

    private void rollDayIfNeeded(LocalDate today) {
        LocalDate current = day.get();
        if (!today.equals(current) && day.compareAndSet(current, today)) {
            used.set(0);
        }
    }
}
