package com.career.recommendation.service;

import com.career.recommendation.util.ServiceTime;
import com.career.recommendation.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 마감이 지난 활동을 매일 자정 직후 비활성화한다.
 *
 * 조회 쿼리(findOpenActivities 등)는 이미 deadline >= today로 거르지만, is_active만 보는
 * 경로(findByIsActiveTrue·관리 목록·시드 점검)가 남아 있고, 한 번 마감된 활동이 "활성"으로
 * 영원히 남는 것 자체가 데이터 상태를 오해하게 만든다. 날짜 기준은 서비스 시간대(Asia/Seoul)로
 * D-day 자정 박제 규칙(v8)과 같다 — 마감일 당일은 아직 유효하고, 다음날 00:05에 닫힌다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityDeadlineScheduler {

    private final ActivityRepository activityRepository;

    @Scheduled(cron = "0 5 0 * * *", zone = ServiceTime.ZONE)
    @Transactional
    public void deactivateExpiredActivities() {
        int closed = activityRepository.deactivateExpired(LocalDate.now(ServiceTime.ZONE_ID));
        if (closed > 0) {
            log.info("마감 지난 활동 {}건을 비활성화했습니다.", closed);
        }
    }
}
