package com.career.recommendation.service;

import com.career.recommendation.dto.roadmap.RoadmapResponse;
import com.career.recommendation.entity.RoadmapCache;
import com.career.recommendation.entity.User;
import com.career.recommendation.repository.RoadmapCacheRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 로드맵 캐시 저장 전용 서비스.
 *
 * RoadmapService는 @Transactional이 없다 — DB 조회는 각 리포지토리 메서드의 기본
 * 트랜잭션에 맡기고, Gemini API 호출(느린 외부 호출)은 트랜잭션 바깥에서 수행해 DB
 * 커넥션을 점유하지 않는다. 그 흐름 중간에 있는 이 저장 로직만은 REQUIRES_NEW로 독립된
 * 트랜잭션이 필요하고, 별도 Bean으로 분리한 이유는 자기 자신을 호출(self-invocation)하면
 * Spring 프록시가 트랜잭션 어드바이스를 가로채지 못하기 때문이다(RecommendationCacheService와
 * 같은 이유).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoadmapCacheService {

    private final RoadmapCacheRepository roadmapCacheRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(User user, RoadmapResponse response) {
        if (response == null || response.getTimeline() == null || response.getTimeline().isEmpty()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(response);
            RoadmapCache rec = roadmapCacheRepository.findByUser_Id(user.getId())
                    .orElseGet(() -> RoadmapCache.builder().user(user).build());

            // ⚠️ 알려진 경합: RecommendationCacheService.save()와 동일 — 같은 유저의 동시 요청이
            // 카운트 증가분 하나를 잃을 수 있으나(read-then-write, 락 없음) 서비스가 죽지는
            // 않는다. 원자적 UPSERT 전환은 로컬 DB 검증이 필요해 이번 세션에선 보류한다.
            java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
            if (today.equals(rec.getLastUpdatedDate())) {
                rec.setDailyUpdateCount(rec.getDailyUpdateCount() != null ? rec.getDailyUpdateCount() + 1 : 1);
            } else {
                rec.setDailyUpdateCount(1);
                rec.setLastUpdatedDate(today);
            }

            rec.setResultJson(json);
            // save()가 아니라 saveAndFlush()를 쓴다 — 신규 RoadmapCache의 INSERT가 이 메서드
            // 반환 후 트랜잭션 커밋 시점(try/catch 바깥)까지 지연되면, user_id UNIQUE 제약을
            // 동시 요청이 위반할 때 DataIntegrityViolationException이 catch를 건너뛰고
            // 호출부까지 전파돼 이미 계산된 응답을 버리고 500이 난다. RecommendationCacheService와
            // 같은 이유로 같은 수정을 적용한다.
            roadmapCacheRepository.saveAndFlush(rec);
        } catch (Exception e) {
            log.warn("로드맵 캐시 저장 실패 (서비스 영향 없음): {}", e.getMessage());
        }
    }
}
