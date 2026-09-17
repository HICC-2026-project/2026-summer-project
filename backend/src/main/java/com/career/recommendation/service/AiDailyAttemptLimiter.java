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

    /**
     * E11-6 — 경험 심층 질문(POST .../questions)과 보강(POST .../enrich)이 공유하는 kind.
     * 두 엔드포인트를 합산해서 세야 하므로(BACKLOG.md E11-6: "questions+enrich 합산 하루 10회")
     * 의도적으로 kind를 하나만 둔다.
     */
    public static final String KIND_ENRICH = "ENRICH";

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.ai.daily-attempts-per-user:3}")
    private int dailyLimit;

    /**
     * KIND_ENRICH 전용 상한. RECOMMENDATION/ROADMAP(하루 3회, 재시도까지 감안한 값)과 성격이
     * 달라 — 질문 2~3개 + 보강 1회가 한 경험당 최소 2회를 쓰고, 경험을 여러 개 입력하면
     * 금방 소진된다 — 별도 기본값(10)을 둔다.
     */
    @Value("${app.ai.daily-attempts-experience-enrich:10}")
    private int enrichDailyLimit;

    /**
     * 오늘 시도 1회를 기록하고, 상한 이내면 true. 상한을 넘긴 시도도 기록은 된다(다음 호출도 거절되게).
     *
     * ⚠️ doTryAcquire(내부 헬퍼)는 @Transactional을 달지 않는다 — 여기(진입점)에서만 달아야
     * REQUIRES_NEW가 실제로 걸린다. Spring @Transactional은 프록시 기반 AOP라, 같은 빈 안에서
     * this.메서드()로 호출하면(self-invocation) 프록시를 거치지 않아 애노테이션이 조용히
     * 무시된다(UserSpecService.saveOrUpdateMySpec 주석의 문제와 같은 함정). tryAcquireEnrich도
     * 같은 이유로 진입점 자신에 @Transactional을 달고 있다 — 서로를 호출하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(UUID userId, String kind) {
        return doTryAcquire(userId, kind, dailyLimit);
    }

    /**
     * KIND_ENRICH 전용 진입점 — enrichDailyLimit을 쓴다. tryAcquire(userId, kind)를 호출하지
     * 않는 이유는 위 주석 참고(self-invocation이면 이 메서드의 @Transactional만 적용되고
     * tryAcquire 쪽 애노테이션은 무시되어 결과적으로는 문제없이 동작하지만, 어느 한쪽이라도
     * 나중에 REQUIRES_NEW가 아닌 값으로 바뀌면 조용히 깨지는 암묵적 의존을 피하기 위해
     * 아예 각자 독립적으로 doTryAcquire를 부른다).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquireEnrich(UUID userId) {
        return doTryAcquire(userId, KIND_ENRICH, enrichDailyLimit);
    }

    /** 트랜잭션 경계 없는 순수 로직 — 항상 위 두 진입점 중 하나가 연 트랜잭션 안에서만 실행된다. */
    private boolean doTryAcquire(UUID userId, String kind, int limit) {
        if (limit <= 0) {
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
        boolean allowed = count != null && count <= limit;
        if (!allowed) {
            log.info("사용자 일일 AI 호출 상한 도달: user={}, kind={}, count={}, limit={}", userId, kind, count, limit);
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
