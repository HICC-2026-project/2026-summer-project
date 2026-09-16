package com.career.recommendation.service;

import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.Recommendation;
import com.career.recommendation.entity.RefreshToken;
import com.career.recommendation.entity.RoadmapCache;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.repository.PasserDataRepository;
import com.career.recommendation.repository.RecommendationRepository;
import com.career.recommendation.repository.RefreshTokenRepository;
import com.career.recommendation.repository.RoadmapCacheRepository;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserRepository;
import com.career.recommendation.repository.UserSpecRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 탈퇴가 실제 FK 규칙(V2·V3·V7·V22 CASCADE, V20·V21 SET NULL)대로 동작하는지 DB에서 본다.
 * V22 이전에는 recommendations·roadmap_caches FK에 CASCADE가 없어 탈퇴가 FK 위반으로 실패했다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class UserServiceDeleteMeTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserSpecRepository userSpecRepository;
    @Autowired private TargetJobRepository targetJobRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RecommendationRepository recommendationRepository;
    @Autowired private RoadmapCacheRepository roadmapCacheRepository;
    @Autowired private PasserDataRepository passerDataRepository;
    @Autowired private EntityManager em;

    @Test
    void 탈퇴하면_개인_파생_데이터는_지워지고_제보한_합격자_데이터는_익명으로_남는다() {
        User user = userRepository.save(User.builder()
                .nickname("탈퇴자").provider("KAKAO").providerId("withdraw-" + System.nanoTime()).build());
        UUID userId = user.getId();

        userSpecRepository.save(UserSpec.builder().user(user).gpa(new BigDecimal("3.5")).build());
        targetJobRepository.save(TargetJob.builder().user(user).jobType("BACKEND").companySize("대기업").industry("IT").build());
        refreshTokenRepository.save(RefreshToken.builder().user(user).token("rt-" + userId)
                .expiresAt(LocalDateTime.now().plusDays(7)).build());
        recommendationRepository.save(Recommendation.builder().user(user).resultJson("{\"activities\":[]}")
                .createdAt(LocalDateTime.now()).build());
        roadmapCacheRepository.save(RoadmapCache.builder().user(user).resultJson("{\"timeline\":[]}")
                .createdAt(LocalDateTime.now()).build());
        PasserData report = passerDataRepository.save(PasserData.builder()
                .reporter(user).jobType("BACKEND").year(2026).isVerified(false).dataOrigin("USER_REPORT")
                .languageScores(List.of()).certifications(new String[0]).build());
        em.flush();

        userService.deleteMe(auth(userId));
        em.flush();
        em.clear();

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(userSpecRepository.findByUser_Id(userId)).isEmpty();
        assertThat(targetJobRepository.findByUser_Id(userId)).isEmpty();
        assertThat(recommendationRepository.findByUser_Id(userId)).isEmpty();
        assertThat(roadmapCacheRepository.findByUser_Id(userId)).isEmpty();
        assertThat(countTokens(userId)).isZero();

        PasserData kept = passerDataRepository.findById(report.getId()).orElseThrow();
        assertThat(kept.getReporter()).isNull();
        assertThat(kept.getJobType()).isEqualTo("BACKEND");
    }

    private long countTokens(UUID userId) {
        return em.createQuery("SELECT COUNT(t) FROM RefreshToken t WHERE t.user.id = :id", Long.class)
                .setParameter("id", userId).getSingleResult();
    }

    private Authentication auth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
