package com.career.recommendation.service;

import com.career.recommendation.dto.admin.ActivityFeedbackSummaryResponse;
import com.career.recommendation.dto.recommendation.RecommendationFeedbackRequest;
import com.career.recommendation.entity.Activity;
import com.career.recommendation.entity.RecommendationFeedback;
import com.career.recommendation.entity.User;
import com.career.recommendation.exception.ActivityNotFoundException;
import com.career.recommendation.repository.ActivityRepository;
import com.career.recommendation.repository.RecommendationFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * E10-2(F-09) — 활동에 대한 사용자 반응(LIKE/DISLIKE) 등록·해제와 관리자 집계.
 *
 * 7/14 "유저당 1건 + 24시간 캐시" 결정을 유지한다 — 피드백을 남겨도 recommendations 캐시를
 * 즉시 재생성하지 않는다. FE에는 "다음 추천 갱신 때 반영돼요"만 안내하고, 실제 반영은
 * PromptDataBuilder가 다음 Gemini 호출 시점에 이 테이블을 읽어서 한다(RecommendationService·
 * RoadmapService 참고).
 *
 * 반응은 활동당 1개뿐이다 — 다시 누르면 이 행을 갱신(upsert)하고, 같은 반응을 재클릭할 때
 * 해제(삭제)하는 토글 판단은 FE가 현재 myReaction을 보고 POST/DELETE 중 무엇을 부를지 정한다.
 */
@Service
@RequiredArgsConstructor
public class RecommendationFeedbackService {

    private final CurrentUserService currentUserService;
    private final RecommendationFeedbackRepository recommendationFeedbackRepository;
    private final ActivityRepository activityRepository;

    @Transactional
    public void upsert(Authentication authentication, UUID activityId, RecommendationFeedbackRequest request) {
        User user = currentUserService.getCurrentUser(authentication);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ActivityNotFoundException(activityId));

        RecommendationFeedback feedback = recommendationFeedbackRepository
                .findByUser_IdAndActivity_Id(user.getId(), activityId)
                .orElseGet(() -> RecommendationFeedback.builder().user(user).activity(activity).build());
        feedback.setReaction(request.getReaction().name());
        recommendationFeedbackRepository.save(feedback);
    }

    @Transactional
    public void delete(Authentication authentication, UUID activityId) {
        User user = currentUserService.getCurrentUser(authentication);
        if (!activityRepository.existsById(activityId)) {
            throw new ActivityNotFoundException(activityId);
        }
        recommendationFeedbackRepository.deleteByUser_IdAndActivity_Id(user.getId(), activityId);
    }

    /** 관리자 집계 — 활동별 LIKE/DISLIKE 수, dislike 많은 순. */
    @Transactional(readOnly = true)
    public List<ActivityFeedbackSummaryResponse> summarizeByActivity() {
        return recommendationFeedbackRepository.summarizeByActivity().stream()
                .map(row -> ActivityFeedbackSummaryResponse.builder()
                        .activityId(row.getActivityId())
                        .activityName(row.getActivityName())
                        .likeCount(row.getLikeCount())
                        .dislikeCount(row.getDislikeCount())
                        .build())
                .toList();
    }
}
