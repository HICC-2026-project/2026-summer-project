package com.career.recommendation.service;

import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.entity.Recommendation;
import com.career.recommendation.entity.User;
import com.career.recommendation.repository.RecommendationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 추천 캐시 저장 전용 서비스.
 *
 * RecommendationService는 @Transactional(readOnly=true)이라 내부에서
 * saveCache를 호출하면 Spring 프록시가 개입하지 못해 쓰기가 되지 않는다.
 * 별도 Bean으로 분리하면 프록시가 정상 작동한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationCacheService {

    private final RecommendationRepository recommendationRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void save(User user, RecommendationResponse response, int cacheHours) {
        try {
            String json = objectMapper.writeValueAsString(response);
            Recommendation rec = recommendationRepository.findByUser_Id(user.getId())
                    .orElseGet(() -> Recommendation.builder().user(user).build());
            rec.setResultJson(json);
            rec.setExpiresAt(LocalDateTime.now().plusHours(cacheHours));
            recommendationRepository.save(rec);
        } catch (Exception e) {
            log.warn("추천 캐시 저장 실패 (서비스 영향 없음): {}", e.getMessage());
        }
    }
}
