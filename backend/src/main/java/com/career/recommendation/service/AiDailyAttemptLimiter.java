package com.career.recommendation.service;

import com.career.recommendation.util.ServiceTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 사용자별 하루 Gemini 호출 시도 상한(기본 3회). 성공·실패와 무관하게 "시도"를 센다.
 *
 * 예전 게이트(recommendations/roadmap_caches.daily_update_count)는 캐시 저장 성공 시에만 증가해서,
 * Gemini가 장애로 폴백만 돌려주는 동안에는 0에 머물렀다 — 한 사용자가 새로고침할 때마다 Gemini를
 * 2회(재시도 포함)씩 불러도 막지 못했다. 여기서는 호출 직전에 원자적 UPSERT로 카운트를 올리고
 * 상한을 넘으면 호출을 건너뛰게 한다. REQUIRES_NEW로 커밋하므로 호출부가 트랜잭션 밖(Gemini 호출은
 * 트랜잭션 밖에서 한다)이어도 즉시 반영된다.
 *
 * 전역 상한(GeminiDailyQuota)과는 층이 다르다 — 이건 한 사용자의 남용·재시도 폭주를, 전역은 서비스
 * 전체 비용을 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDailyAttemptLimiter {

    public static final String KIND_RECOMMENDATION = "RECOMMENDATION";
    public static final String KIND_ROADMAP = "ROADMAP";

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.ai.daily-attempts-per-user:3}")
    private int dailyLimit;

    /** 오늘 시도 1회를 기록하고, 상한 이내면 true. 상한을 넘긴 시도도 기록은 된다(다음 호출도 거절되게). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(UUID userId, String kind) {
        if (dailyLimit <= 0) {
            return true;
        }
        LocalDate today = LocalDate.now(ServiceTime.ZONE_ID);
        Integer count = jdbcTemplate.queryForObject("""
                INSERT INTO ai_daily_attempts (user_id, kind, attempt_date, attempt_count)
                VALUES (?, ?, ?, 1)
                ON CONFLICT (user_id, kind, attempt_date)
                DO UPDATE SET attempt_count = ai_daily_attempts.attempt_count + 1
                RETURNING attempt_count
                """, Integer.class, userId, kind, today);
        boolean allowed = count != null && count <= dailyLimit;
        if (!allowed) {
            log.info("사용자 일일 AI 호출 상한 도달: user={}, kind={}, count={}", userId, kind, count);
        }
        return allowed;
    }

    /** 오늘 이 사용자의 시도 수(게이트 판정 없이 조회). */
    @Transactional(readOnly = true)
    public int attemptsToday(UUID userId, String kind) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(attempt_count), 0) FROM ai_daily_attempts
                WHERE user_id = ? AND kind = ? AND attempt_date = ?
                """, Integer.class, userId, kind, LocalDate.now(ServiceTime.ZONE_ID));
        return count == null ? 0 : count;
    }

    /** 지난 기록은 쓸모가 없으므로 매일 정리한다(테이블이 사용자×일수만큼 자라지 않게). */
    @Scheduled(cron = "0 30 0 * * *", zone = ServiceTime.ZONE)
    @Transactional
    public void purgeOld() {
        int deleted = jdbcTemplate.update("DELETE FROM ai_daily_attempts WHERE attempt_date < ?",
                LocalDate.now(ServiceTime.ZONE_ID).minusDays(2));
        if (deleted > 0) {
            log.info("지난 AI 호출 시도 기록 {}건 정리", deleted);
        }
    }
}
