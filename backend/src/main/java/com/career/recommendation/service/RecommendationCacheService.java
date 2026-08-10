package com.career.recommendation.service;

import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.entity.Recommendation;
import com.career.recommendation.entity.User;
import com.career.recommendation.repository.RecommendationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 추천 캐시 저장 전용 서비스.
 *
 * RecommendationService는 @Transactional이 없다 — DB 조회는 각 리포지토리 메서드의
 * 기본 트랜잭션에 맡기고, Gemini API 호출(느린 외부 호출)은 트랜잭션 바깥에서 수행해
 * DB 커넥션을 점유하지 않는다. 그 흐름 중간에 있는 이 저장 로직만은 REQUIRES_NEW로
 * 독립된 트랜잭션이 필요하다 — 같은 요청 안에서 조회 트랜잭션과 저장 트랜잭션이 섞이지
 * 않고, 저장 실패가 앞선 조회 결과에 영향을 주지 않게 하기 위함이다. 별도 Bean으로
 * 분리한 이유는 자기 자신을 호출(self-invocation)하면 Spring 프록시가 트랜잭션
 * 어드바이스를 가로채지 못하기 때문이다 — REQUIRES_NEW를 같은 클래스 내부 메서드로
 * 두면 이 프록시 우회 문제가 그대로 재현된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationCacheService {

    private final RecommendationRepository recommendationRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(User user, RecommendationResponse response) {
        if (response == null || response.getActivities() == null || response.getActivities().isEmpty()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(response);
            Recommendation rec = recommendationRepository.findByUser_Id(user.getId())
                    .orElseGet(() -> Recommendation.builder().user(user).build());
            
            java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
            if (today.equals(rec.getLastUpdatedDate())) {
                rec.setDailyUpdateCount(rec.getDailyUpdateCount() != null ? rec.getDailyUpdateCount() + 1 : 1);
            } else {
                rec.setDailyUpdateCount(1);
                rec.setLastUpdatedDate(today);
            }
            
            rec.setResultJson(json);
            recommendationRepository.save(rec);
        } catch (Exception e) {
            log.warn("추천 캐시 저장 실패 (서비스 영향 없음): {}", e.getMessage());
        }
    }
}
