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
 * RoadmapService는 @Transactional(readOnly=true)이라 내부에서
 * saveCache를 호출하면 Spring 프록시가 개입하지 못해 쓰기가 되지 않는다.
 * 별도 Bean으로 분리하면 프록시가 정상 작동한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoadmapCacheService {

    private final RoadmapCacheRepository roadmapCacheRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(User user, RoadmapResponse response, int cacheHours) {
        if (response == null || response.getTimeline() == null || response.getTimeline().isEmpty()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(response);
            RoadmapCache rec = roadmapCacheRepository.findByUser_Id(user.getId())
                    .orElseGet(() -> RoadmapCache.builder().user(user).build());
            rec.setResultJson(json);
            rec.setExpiresAt(LocalDateTime.now().plusHours(cacheHours));
            roadmapCacheRepository.save(rec);
        } catch (Exception e) {
            log.warn("로드맵 캐시 저장 실패 (서비스 영향 없음): {}", e.getMessage());
        }
    }
}
