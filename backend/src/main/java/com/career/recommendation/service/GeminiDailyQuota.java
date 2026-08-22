package com.career.recommendation.service;

import com.career.recommendation.util.ServiceTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
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
    /** 상한 도달 경고를 하루 한 번만 남기기 위한 플래그. 날짜가 바뀌면 리셋. */
    private final AtomicBoolean limitLogged = new AtomicBoolean();

    public GeminiDailyQuota(@Value("${gemini.api.daily-limit:500}") int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    /** 호출 허용 여부. 허용되면 오늘 사용량을 1 올린다. 0 이하 설정은 "상한 없음". */
    public boolean tryAcquire() {
        if (dailyLimit <= 0) {
            return true;
        }
        rollDayIfNeeded(LocalDate.now(ServiceTime.ZONE_ID));
        // 증가 후 되돌리는 방식은 경합 시 카운터가 잠깐 상한을 넘고 경고가 여러 번 찍힌다 — CAS 한 번으로 "상한 미만일 때만 +1".
        int before = used.getAndUpdate(n -> n < dailyLimit ? n + 1 : n);
        if (before < dailyLimit) {
            return true;
        }
        if (limitLogged.compareAndSet(false, true)) {
            log.warn("Gemini 일일 호출 상한({})에 도달 — 이후 요청은 폴백 추천으로 처리합니다.", dailyLimit);
        }
        return false;
    }

    public int dailyLimit() {
        return dailyLimit;
    }

    public int usedToday() {
        rollDayIfNeeded(LocalDate.now(ServiceTime.ZONE_ID));
        return used.get();
    }

    private void rollDayIfNeeded(LocalDate today) {
        LocalDate current = day.get();
        if (!today.equals(current) && day.compareAndSet(current, today)) {
            used.set(0);
            limitLogged.set(false);
        }
    }
}
