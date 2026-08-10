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

            // ⚠️ 알려진 경합: 같은 유저의 요청 두 개가 거의 동시에 findByUser_Id에서 둘 다
            // "없음"을 보면(신규 유저의 첫 두 요청, 더블클릭 등) 카운트 증가분 하나가 유실될 수
            // 있다(read-then-write, 락 없음) — 최악의 경우 하루 3회 제한이 4~5회로 느슨해질 뿐,
            // 서비스가 죽지는 않는다. 원자적 UPSERT로 고치려면 로컬 DB로 실제 검증이 필요해
            // 지금 세션에선 보류한다.
            java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
            if (today.equals(rec.getLastUpdatedDate())) {
                rec.setDailyUpdateCount(rec.getDailyUpdateCount() != null ? rec.getDailyUpdateCount() + 1 : 1);
            } else {
                rec.setDailyUpdateCount(1);
                rec.setLastUpdatedDate(today);
            }

            rec.setResultJson(json);
            // save()가 아니라 saveAndFlush()를 쓴다. save()만 쓰면 신규 Recommendation의 실제
            // INSERT가 (UUID는 애플리케이션에서 미리 생성되지만) 이 메서드가 정상 반환된 뒤,
            // REQUIRES_NEW 트랜잭션이 커밋되는 시점에야 나간다 — 그 시점은 이미 아래 try/catch
            // 바깥이라, user_id UNIQUE 제약을 두 요청이 동시에 위반하면(첫 추천 생성 시
            // 더블클릭·네트워크 재시도 등) DataIntegrityViolationException이 catch를 건너뛰고
            // 호출부(비트랜잭션인 RecommendationService)까지 그대로 전파되어 이미 정상 계산된
            // 응답을 버리고 500이 났다. saveAndFlush()로 INSERT를 이 메서드 안에서 강제
            // 실행시키면 그 예외가 catch 범위 안에서 잡혀 "캐시 저장만 실패, 응답은 정상 200"이
            // 된다 — 클래스 상단 주석이 원래 의도한 "저장 실패가 서비스에 영향 없음"이 실제로
            // 지켜진다.
            recommendationRepository.saveAndFlush(rec);
        } catch (Exception e) {
            log.warn("추천 캐시 저장 실패 (서비스 영향 없음): {}", e.getMessage());
        }
    }
}
