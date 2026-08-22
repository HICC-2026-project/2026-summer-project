package com.career.recommendation.service;

import com.career.recommendation.entity.Activity;
import com.career.recommendation.repository.ActivityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** ActivityDeadlineScheduler가 매일 부르는 bulk UPDATE가 "마감일 당일은 유지, 전날까지는 닫기"를 지키는지 실제 DB에서 본다. */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ActivityDeadlineSchedulerTest {

    @Autowired
    private ActivityRepository activityRepository;

    @Test
    void 마감_지난_활성_활동만_닫고_당일_마감과_마감_없음은_남긴다() {
        LocalDate today = LocalDate.of(2026, 8, 22);
        Activity expired = save("expired-" + UUID.randomUUID(), today.minusDays(1), true);
        Activity dueToday = save("today-" + UUID.randomUUID(), today, true);
        Activity noDeadline = save("none-" + UUID.randomUUID(), null, true);
        Activity alreadyClosed = save("closed-" + UUID.randomUUID(), today.minusDays(30), false);

        int closed = activityRepository.deactivateExpired(today);

        // 이 테스트가 만든 만료 건 1개는 반드시 포함된다(시드에 다른 만료 건이 있으면 더 클 수 있다).
        assertThat(closed).isGreaterThanOrEqualTo(1);
        assertThat(activityRepository.findById(expired.getId()).orElseThrow().getIsActive()).isFalse();
        assertThat(activityRepository.findById(dueToday.getId()).orElseThrow().getIsActive()).isTrue();
        assertThat(activityRepository.findById(noDeadline.getId()).orElseThrow().getIsActive()).isTrue();
        assertThat(activityRepository.findById(alreadyClosed.getId()).orElseThrow().getIsActive()).isFalse();
    }

    private Activity save(String name, LocalDate deadline, boolean active) {
        return activityRepository.saveAndFlush(Activity.builder()
                .type("EXTERNAL")
                .name(name)
                .organization("테스트")
                .deadline(deadline)
                .isActive(active)
                .build());
    }
}
