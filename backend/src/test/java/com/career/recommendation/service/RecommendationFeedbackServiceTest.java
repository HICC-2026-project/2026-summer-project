package com.career.recommendation.service;

import com.career.recommendation.domain.ReactionType;
import com.career.recommendation.dto.admin.ActivityFeedbackSummaryResponse;
import com.career.recommendation.dto.recommendation.RecommendationFeedbackRequest;
import com.career.recommendation.entity.Activity;
import com.career.recommendation.entity.RecommendationFeedback;
import com.career.recommendation.entity.User;
import com.career.recommendation.exception.ActivityNotFoundException;
import com.career.recommendation.repository.ActivityRepository;
import com.career.recommendation.repository.RecommendationFeedbackRepository;
import com.career.recommendation.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E10-2(F-09) 추천 피드백 — upsert(등록·변경)·해제·존재하지 않는 활동 처리·관리자 집계를
 * 실제 DB(recommendation_feedback UNIQUE(user_id, activity_id) 제약 포함)에서 검증한다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class RecommendationFeedbackServiceTest {

    @Autowired private RecommendationFeedbackService recommendationFeedbackService;
    @Autowired private RecommendationFeedbackRepository recommendationFeedbackRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private UserRepository userRepository;

    private User saveUser() {
        return userRepository.save(User.builder()
                .nickname("피드백러").provider("KAKAO").providerId("feedback-" + System.nanoTime()).build());
    }

    private Activity saveActivity(String name) {
        return activityRepository.saveAndFlush(Activity.builder()
                .type("EXTERNAL").name(name).organization("테스트")
                .tags(new String[]{"백엔드"}).isActive(true).build());
    }

    private Authentication auth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private RecommendationFeedbackRequest request(ReactionType reaction) {
        RecommendationFeedbackRequest r = new RecommendationFeedbackRequest();
        r.setReaction(reaction);
        return r;
    }

    @Test
    void 처음_반응을_남기면_행이_생성된다() {
        User user = saveUser();
        Activity activity = saveActivity("첫_피드백_활동");

        recommendationFeedbackService.upsert(auth(user.getId()), activity.getId(), request(ReactionType.LIKE));

        RecommendationFeedback saved = recommendationFeedbackRepository
                .findByUser_IdAndActivity_Id(user.getId(), activity.getId()).orElseThrow();
        assertThat(saved.getReaction()).isEqualTo("LIKE");
    }

    @Test
    void 다시_반응을_남기면_같은_행이_갱신된다_upsert() {
        User user = saveUser();
        Activity activity = saveActivity("반응_변경_활동");

        recommendationFeedbackService.upsert(auth(user.getId()), activity.getId(), request(ReactionType.LIKE));
        recommendationFeedbackService.upsert(auth(user.getId()), activity.getId(), request(ReactionType.DISLIKE));

        List<RecommendationFeedback> all = recommendationFeedbackRepository.findByUser_Id(user.getId());
        // UNIQUE(user_id, activity_id) — 행이 하나만 있어야 하고 값은 최신 반응이어야 한다.
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getReaction()).isEqualTo("DISLIKE");
    }

    @Test
    void 해제하면_행이_삭제된다() {
        User user = saveUser();
        Activity activity = saveActivity("해제_대상_활동");
        recommendationFeedbackService.upsert(auth(user.getId()), activity.getId(), request(ReactionType.LIKE));

        recommendationFeedbackService.delete(auth(user.getId()), activity.getId());

        assertThat(recommendationFeedbackRepository.findByUser_IdAndActivity_Id(user.getId(), activity.getId()))
                .isEmpty();
    }

    @Test
    void 존재하지_않는_활동에_피드백을_등록하면_404_예외를_던진다() {
        User user = saveUser();
        UUID missingActivityId = UUID.randomUUID();

        assertThatThrownBy(() ->
                recommendationFeedbackService.upsert(auth(user.getId()), missingActivityId, request(ReactionType.LIKE)))
                .isInstanceOf(ActivityNotFoundException.class);
    }

    @Test
    void 존재하지_않는_활동의_피드백을_해제하면_404_예외를_던진다() {
        User user = saveUser();
        UUID missingActivityId = UUID.randomUUID();

        assertThatThrownBy(() -> recommendationFeedbackService.delete(auth(user.getId()), missingActivityId))
                .isInstanceOf(ActivityNotFoundException.class);
    }

    @Test
    void 관리자_집계는_활동별_LIKE_DISLIKE_수를_dislike_많은_순으로_반환한다() {
        User userA = saveUser();
        User userB = saveUser();
        User userC = saveUser();
        Activity popular = saveActivity("dislike_많은_활동");
        Activity liked = saveActivity("like만_있는_활동");

        recommendationFeedbackService.upsert(auth(userA.getId()), popular.getId(), request(ReactionType.DISLIKE));
        recommendationFeedbackService.upsert(auth(userB.getId()), popular.getId(), request(ReactionType.DISLIKE));
        recommendationFeedbackService.upsert(auth(userC.getId()), popular.getId(), request(ReactionType.LIKE));
        recommendationFeedbackService.upsert(auth(userA.getId()), liked.getId(), request(ReactionType.LIKE));

        List<ActivityFeedbackSummaryResponse> summary = recommendationFeedbackService.summarizeByActivity();

        ActivityFeedbackSummaryResponse popularRow = summary.stream()
                .filter(r -> r.getActivityId().equals(popular.getId())).findFirst().orElseThrow();
        ActivityFeedbackSummaryResponse likedRow = summary.stream()
                .filter(r -> r.getActivityId().equals(liked.getId())).findFirst().orElseThrow();

        assertThat(popularRow.getLikeCount()).isEqualTo(1);
        assertThat(popularRow.getDislikeCount()).isEqualTo(2);
        assertThat(likedRow.getLikeCount()).isEqualTo(1);
        assertThat(likedRow.getDislikeCount()).isEqualTo(0);
        // dislike가 많은 순 정렬 — popular(2)가 liked(0)보다 앞에 와야 한다.
        assertThat(summary.indexOf(popularRow)).isLessThan(summary.indexOf(likedRow));
    }
}
