package com.career.recommendation.service;

import com.career.recommendation.util.ServiceTime;
import com.career.recommendation.dto.gemini.GeminiRecommendationResult;
import com.career.recommendation.dto.gemini.GeminiRecommendationResult.GeminiActivity;
import com.career.recommendation.dto.position.SpecPositionResult;
import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.dto.recommendation.RecommendationResponse.ActivityRecommendation;
import com.career.recommendation.dto.roadmap.RoadmapResponse;
import com.career.recommendation.dto.roadmap.RoadmapResponse.MatchedActivity;
import com.career.recommendation.dto.roadmap.RoadmapResponse.TimelineStep;
import com.career.recommendation.domain.ReactionType;
import com.career.recommendation.entity.Activity;
import com.career.recommendation.entity.Recommendation;
import com.career.recommendation.entity.RecommendationFeedback;
import com.career.recommendation.entity.RoadmapCache;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.repository.ActivityRepository;
import com.career.recommendation.repository.RecommendationFeedbackRepository;
import com.career.recommendation.repository.RecommendationRepository;
import com.career.recommendation.repository.RoadmapCacheRepository;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserSpecRepository;
import com.career.recommendation.util.GapMatcher;
import com.career.recommendation.util.GraduateOnlyActivityFilter;
import com.career.recommendation.util.PromptDataBuilder;
import com.career.recommendation.util.SpecPositionCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * BE-1 담당 — F-03 활동 추천 비즈니스 로직.
 *
 * 비교 계산: 직무 요구 프로필(JobSpecProfileService) 안에서의 percentile 위치·갭
 * (SpecPositionCalculator). 예전의 "유사 합격자 Top 5 검색 + 가중 총점" 체계를 대체했다.
 *
 * 캐시 전략: 유저당 1건, 스펙 변경 시 즉시 갱신 (하루 최대 3회).
 * Gemini 실패 처리: 1회 재시도(2초 백오프) → 2회 연속 실패 시 Fallback 데이터 반환 + isAiRecommendation=false.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final CurrentUserService currentUserService;
    private final UserSpecRepository userSpecRepository;
    private final TargetJobRepository targetJobRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationCacheService recommendationCacheService;
    private final RoadmapCacheRepository roadmapCacheRepository;
    private final ActivityRepository activityRepository;
    private final SpecPositionService specPositionService;
    private final GeminiService geminiService;
    private final PromptDataBuilder promptDataBuilder;
    private final ObjectMapper objectMapper;
    private final AiDailyAttemptLimiter aiDailyAttemptLimiter;
    private final RecommendationFeedbackRepository recommendationFeedbackRepository;

    private static final int MAX_RECOMMENDABLE_ACTIVITIES = 20;
    private static final ZoneId SERVICE_ZONE_ID = ServiceTime.ZONE_ID;

    /**
     * 현재 로그인한 유저의 맞춤 추천 활동 목록을 반환한다.
     * 유효한 캐시가 있으면 DB에서 즉시 반환한다.
     *
     * 트랜잭션 없이 전체 흐름을 오케스트레이션한다.
     * DB 조회는 각 리포지토리 메서드의 기본 트랜잭션에 의존하고,
     * Gemini API 호출은 트랜잭션 바깥에서 수행하여 DB 커넥션을 점유하지 않는다.
     */
    public RecommendationResponse getRecommendations(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);
        LocalDate today = LocalDate.now(SERVICE_ZONE_ID);

        // 1. 유효 캐시 확인 및 업데이트 필요 여부 판별
        Recommendation cached = recommendationRepository.findByUser_Id(user.getId()).orElse(null);
        UserSpec userSpec = userSpecRepository.findByUser_Id(user.getId()).orElse(null);
        TargetJob targetJob = targetJobRepository.findByUser_Id(user.getId()).orElse(null);

        boolean wantsRefresh = false;
        RecommendationResponse cachedResponse = null;

        if (cached == null) {
            wantsRefresh = true;
        } else {
            cachedResponse = deserialize(cached.getResultJson());
            // 캐시는 스펙이 바뀔 때만 재생성되는데, 생성 후 스케줄러가 마감 활동을 비활성화하거나
            // 관리자가 활동을 내리면 캐시가 그 활동을 계속 노출한다(2026-09-17 실사용자 제보:
            // "마감 끝난 활동이 추천에 뜬다"). 캐시를 반환하기 직전, 매번 DB와 대조해 걸러낸다.
            cachedResponse = filterStaleActivities(cachedResponse, today);
            boolean isSpecChanged = isSpecModifiedSince(userSpec, targetJob, cached.getCreatedAt());
            boolean hasUsableActivities = hasUsableCachedActivities(cachedResponse, today);
            // v8 이하(구 점수 체계 — matchScore·compareRows) 캐시는 specPosition이 없어
            // 전부 legacy로 잡힌다. 버전만으로도 충분하지만 specPosition null 체크를 함께 둬,
            // 버전 필드만 살아있고 본문이 깨진 캐시도 재생성 대상이 되게 한다.
            boolean isLegacyCache = cachedResponse == null
                    || cachedResponse.getSpecPosition() == null
                    || cachedResponse.getScoreFormulaVersion() == null
                    || cachedResponse.getScoreFormulaVersion() < SpecPositionCalculator.CURRENT_SCORE_FORMULA_VERSION;

            // ⚠️ isLegacyCache는 예전엔 이 하루 제한 체크를 건너뛰고 무조건 needsNewAiCall=true였다
            // ("한 번만 재생성하니 괜찮다"는 의도). 그런데 폴백 응답은 저장되지 않으므로
            // (isAiRecommendation()==false일 때 recommendationCacheService.save()를 안 부름),
            // Gemini가 장애 중이거나 키 미설정이면 "한 번"이 아니라 그 유저가 요청할 때마다
            // 매번 legacy 판정 → Gemini 2회 호출(2초 sleep 포함, 최대 60초 타임아웃) → 폴백 →
            // 캐시 미저장 → 다음 요청도 다시 legacy, 이 반복이 스스로 끝나지 않았다.
            // scoreFormulaVersion을 올려 배포하는 순간 전 유저 캐시가 동시에 legacy가 되는
            // 시나리오와 겹치면 실제로 밟을 수 있는 경로다. legacy도 같은 하루 3회 게이트를
            // 통과하게 해서, 하루 제한에 도달하면 legacy든 아니든 옛 캐시를 그대로 반환한다.
            wantsRefresh = isLegacyCache || !hasUsableActivities || isSpecChanged;
        }

        // 하루 시도 상한 — 성공·실패와 무관하게 "시도"를 센다(AiDailyAttemptLimiter). 예전 게이트는 저장 성공만
        // 세서 Gemini 장애 중엔 매 요청이 통과했고, 캐시 JSON 파싱 실패(cachedResponse == null)면 아예 우회됐다.
        // 지금은 재호출이 필요한 모든 경로(첫 호출 포함)가 이 한 줄을 지난다.
        boolean needsNewAiCall = wantsRefresh
                && aiDailyAttemptLimiter.tryAcquire(user.getId(), AiDailyAttemptLimiter.KIND_RECOMMENDATION);

        if (!needsNewAiCall && cachedResponse != null) {
            // ⚠️ 재생성이 필요했는데(스펙 변경·legacy·만료 활동) 하루 한도에 막혀 캐시를 주는
            // 경우, 위치·갭은 Gemini와 무관한 로컬 계산이므로 현재 스펙으로 새로 계산해서 준다.
            // 캐시를 통째로 반환하면 프로필에는 새 스펙이 보이는데(user_specs는 즉시 저장됨)
            // 비교 탭 "나" 값은 옛 스펙을 보여줘, 사용자가 "수정이 안 된다"고 인지한다
            // (2026-08-11 실제 제보). 하루 한도는 Gemini 호출(활동 목록 재생성)에만 적용한다.
            // dailyLimitReached 플래그로 FE가 "활동 목록은 내일 갱신" 안내를 띄울 수 있게 한다.
            if (wantsRefresh) {
                return applyMyReactions(mergeRoadmapActivities(
                        rebuildPositionFromCurrentSpec(cachedResponse, userSpec, targetJob), user.getId(), today), user.getId());
            }
            return applyMyReactions(mergeRoadmapActivities(cachedResponse, user.getId(), today), user.getId());
        }

        // 3. 직무 요구 프로필 조회(캐시됨) 및 위치·갭 계산 — 로드맵과 같은 진입점을 쓴다.
        String jobType = (targetJob != null) ? targetJob.getJobType() : null;
        SpecPositionResult position = specPositionService.calculate(userSpec, jobType);

        // 4. 현재 신청 가능한 DB 활동 조회 (RAG 패턴 — Gemini에 선택지 제공)
        List<Activity> activeActivities = activityRepository.findRecommendableActivities(
                today,
                PageRequest.of(0, MAX_RECOMMENDABLE_ACTIVITIES)
        );
        // 재학생(1~3학년)은 "학사 학위 이상/졸업예정자 전용" 대졸 공채에 지원할 수 없다 — 후보 선정
        // 단계에서 미리 제외한다(로드맵과 공통 헬퍼, GraduateOnlyActivityFilter 참고).
        activeActivities = GraduateOnlyActivityFilter.filterForGrade(
                activeActivities, userSpec != null ? userSpec.getGrade() : null);
        String availableActivitiesJson = promptDataBuilder.buildAvailableActivitiesJson(activeActivities);

        // 5. Gemini API 호출 (최대 2회 시도) — 프롬프트에도 화면과 같은 위치·갭 데이터를 준다.
        String userSpecJson = promptDataBuilder.serializeSpecForRecommendation(userSpec);
        String targetJobStr = promptDataBuilder.buildTargetJobString(targetJob);
        String positionContext = promptDataBuilder.buildPositionContextText(position);

        // 한도에 막혔는데 캐시까지 없거나 깨진 경우(위 조기 반환을 못 탄 경우): Gemini 없이 규칙 기반 폴백을 준다.
        RecommendationResponse response;
        if (needsNewAiCall) {
            // E10-2(F-09) — 사용자가 남긴 활동 피드백을 프롬프트에 반영한다. Gemini를 실제로
            // 부를 때만 조회한다(폴백 경로는 규칙 기반이라 필요 없다).
            String feedbackContext = buildFeedbackContext(user.getId());
            response = callGeminiWithRetry(
                    userSpecJson, targetJobStr, positionContext, feedbackContext, availableActivitiesJson,
                    position, activeActivities,
                    jobType != null ? jobType : "미설정", today);
        } else {
            response = buildFallbackResponse(activeActivities, position, jobType != null ? jobType : "미설정")
                    .toBuilder().dailyLimitReached(true).build();
        }

        // 6. 결과 캐싱 — 별도 Bean에서 호출. (daily_update_count는 통계용으로만 남아 있고 하루 게이트는 AiDailyAttemptLimiter가 맡는다)
        if (response.isAiRecommendation()) {
            recommendationCacheService.save(user, response);
        }

        return applyMyReactions(mergeRoadmapActivities(response, user.getId(), today), user.getId());
    }

    /**
     * E10-2(F-09) — 유저가 남긴 반응(LIKE/DISLIKE)을 최근 갱신순 상한 개수까지 조회해
     * Gemini 프롬프트용 텍스트로 변환한다. RoadmapService도 같은 방식으로 조회해 추천·로드맵이
     * 같은 피드백 신호를 보게 한다.
     */
    private String buildFeedbackContext(UUID userId) {
        List<Activity> liked = recommendationFeedbackRepository
                .findByUser_IdAndReactionOrderByUpdatedAtDesc(
                        userId, ReactionType.LIKE.name(),
                        PageRequest.of(0, PromptDataBuilder.FEEDBACK_ACTIVITY_PROMPT_LIMIT))
                .stream().map(RecommendationFeedback::getActivity).toList();
        List<Activity> disliked = recommendationFeedbackRepository
                .findByUser_IdAndReactionOrderByUpdatedAtDesc(
                        userId, ReactionType.DISLIKE.name(),
                        PageRequest.of(0, PromptDataBuilder.FEEDBACK_ACTIVITY_PROMPT_LIMIT))
                .stream().map(RecommendationFeedback::getActivity).toList();
        return promptDataBuilder.buildFeedbackContextText(liked, disliked);
    }

    /**
     * E10-2(F-09) — 응답의 각 활동에 지금 로그인한 유저가 남긴 반응(myReaction)을 채운다.
     * dailyLimitReached와 같은 "반환 전용" 패턴이다 — 캐시(Recommendation.resultJson)에는
     * 절대 싣지 않는다. 반응은 캐시 재생성 없이도 즉시 바뀔 수 있어야 하므로, 캐시를 읽고
     * 최종 응답을 만드는 모든 경로의 맨 마지막에 이 메서드를 거친다.
     */
    private RecommendationResponse applyMyReactions(RecommendationResponse response, UUID userId) {
        if (response == null || response.getActivities() == null || response.getActivities().isEmpty()) {
            return response;
        }
        Map<UUID, String> reactions = recommendationFeedbackRepository.findByUser_Id(userId).stream()
                .collect(Collectors.toMap(f -> f.getActivity().getId(), RecommendationFeedback::getReaction));
        if (reactions.isEmpty()) {
            return response;
        }
        List<ActivityRecommendation> withReactions = response.getActivities().stream()
                .map(a -> (a.getId() != null && reactions.containsKey(a.getId()))
                        ? a.toBuilder().myReaction(reactions.get(a.getId())).build()
                        : a)
                .toList();
        return response.toBuilder().activities(withReactions).build();
    }

    /**
     * 홈 추천 응답을 반환하기 직전, 같은 유저의 로드맵 캐시에 있는 매칭 활동 중 추천 목록에
     * 아직 없는 것을 뒤에 덧붙인다. 실사용 피드백(2026-09-17): "추천이랑 로드맵이 띄워주는 게
     * 달라. 둘 다 괜찮은 제안인데, 적어도 홈 화면엔 로드맵에 떠 있는 건 모두 떴으면 좋겠다."
     *
     * 로드맵 생성 시엔 이미 추천된 활동을 프롬프트에서 제외(excludeIds)하기 때문에 두 목록이
     * 의도적으로 갈라진다 — 이 dedup 자체는 그대로 둔다. 대신 "홈에 보여줄 목록"을 만드는
     * 이 시점에만 로드맵 표시 활동을 합쳐 보여준다.
     *
     * 읽기 시점 표시용이다: 로드맵을 새로 생성하지 않고(Gemini 호출 유발 금지) 이미 있는
     * 로드맵 캐시만 참고하며, 병합 결과는 추천 캐시에 다시 저장하지 않는다 — 로드맵이 바뀌면
     * 다음 조회에서 자동으로 반영되게 하기 위함이다.
     */
    private RecommendationResponse mergeRoadmapActivities(
            RecommendationResponse response, UUID userId, LocalDate today) {
        if (response == null) {
            return response;
        }

        RoadmapCache roadmapCache = roadmapCacheRepository.findByUser_Id(userId).orElse(null);
        if (roadmapCache == null) {
            return response; // 로드맵을 아직 생성한 적 없음 — 병합할 것이 없다.
        }

        RoadmapResponse roadmap = deserializeRoadmap(roadmapCache.getResultJson());
        if (roadmap == null || roadmap.getTimeline() == null || roadmap.getTimeline().isEmpty()) {
            return response;
        }

        Set<UUID> existingIds = new HashSet<>();
        if (response.getActivities() != null) {
            for (ActivityRecommendation a : response.getActivities()) {
                if (a.getId() != null) {
                    existingIds.add(a.getId());
                }
            }
        }

        // 추천 목록에 없는 로드맵 매칭 활동을 등장 순서대로 모은다. 같은 활동이 여러 스텝에
        // 걸쳐 있으면 가장 먼저 등장한 스텝의 맥락(순위·이유)만 사용한다.
        List<TimelineStep> timeline = roadmap.getTimeline();
        List<UUID> candidateOrder = new ArrayList<>();
        Map<UUID, Integer> firstStepIndexByActivity = new HashMap<>();
        for (int i = 0; i < timeline.size(); i++) {
            TimelineStep step = timeline.get(i);
            if (step == null || step.getMatchedActivities() == null) {
                continue;
            }
            for (MatchedActivity ma : step.getMatchedActivities()) {
                UUID activityId = (ma != null) ? ma.getActivityId() : null;
                if (activityId == null || existingIds.contains(activityId)) {
                    continue;
                }
                if (!firstStepIndexByActivity.containsKey(activityId)) {
                    firstStepIndexByActivity.put(activityId, i);
                    candidateOrder.add(activityId);
                }
            }
        }
        if (candidateOrder.isEmpty()) {
            return response;
        }

        // DB와 대조해 비활성·마감·삭제된 활동은 제외한다(filterStaleActivities와 같은 기준).
        Map<UUID, Activity> dbActivities = activityRepository.findAllById(candidateOrder).stream()
                .collect(Collectors.toMap(Activity::getId, a -> a));

        List<ActivityRecommendation> merged = new ArrayList<>(
                response.getActivities() != null ? response.getActivities() : List.of());
        for (UUID activityId : candidateOrder) {
            Activity db = dbActivities.get(activityId);
            if (!isActivityUsable(db, today)) {
                continue;
            }

            int stepIndex = firstStepIndexByActivity.get(activityId);
            TimelineStep step = timeline.get(stepIndex);
            String reasonContext = (step.getReason() != null && !step.getReason().isBlank())
                    ? step.getReason()
                    : step.getActivity();
            String reason = (reasonContext != null && !reasonContext.isBlank())
                    ? String.format("로드맵 %d번째 시기 활동 · %s", stepIndex + 1, reasonContext)
                    : String.format("로드맵 %d번째 시기 활동입니다.", stepIndex + 1);

            merged.add(ActivityRecommendation.builder()
                    .id(db.getId())
                    .type(db.getType())
                    .name(db.getName())
                    .reason(reason)
                    .deadline(db.getDeadline())
                    .targetGap(null)
                    .build());
        }

        return response.toBuilder().activities(merged).build();
    }

    private RoadmapResponse deserializeRoadmap(String json) {
        try {
            return objectMapper.readValue(json, RoadmapResponse.class);
        } catch (Exception e) {
            log.warn("로드맵 캐시 역직렬화 실패 → 홈 추천 병합 생략: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 캐시된 활동 목록은 유지하되, 위치·갭 등 로컬에서 결정적으로 계산되는 부분만 현재 스펙
     * 기준으로 다시 계산해 돌려준다. 하루 갱신 한도에 막혀 Gemini 재호출은 못 하는 상황에서,
     * 최소한 사용자가 방금 바꾼 스펙이 비교 탭에는 즉시 반영되게 하기 위한 경로다.
     * toBuilder 결과는 반환 전용이며 캐시에 저장하지 않는다(저장하면 dailyLimitReached가
     * 캐시에 박제됨 — DTO 주석 참고).
     */
    private RecommendationResponse rebuildPositionFromCurrentSpec(
            RecommendationResponse cachedResponse, UserSpec userSpec, TargetJob targetJob) {
        String jobType = (targetJob != null) ? targetJob.getJobType() : null;
        SpecPositionResult position = specPositionService.calculate(userSpec, jobType);

        // 갭이 새 스펙 기준으로 바뀌었으니 카드의 targetGap도 다시 맞춘다 — 사용자가 방금 딴 자격증이
        // 비교 탭에서는 사라졌는데 추천 카드엔 "이 갭을 줄여요"로 남아 있으면 두 화면이 모순된다.
        List<GapMatcher.Gap> knownGaps = GapMatcher.knownGaps(position);
        List<ActivityRecommendation> realigned = cachedResponse.getActivities() == null ? null
                : cachedResponse.getActivities().stream()
                .map(a -> a.toBuilder()
                        .targetGap(GapMatcher.normalizeTargetGap(a.getTargetGap(), knownGaps).orElse(null))
                        .build())
                .toList();

        return cachedResponse.toBuilder()
                .activities(realigned)
                .specPosition(position)
                .targetJobName(jobType != null ? jobType : "미설정")
                .scoreFormulaVersion(SpecPositionCalculator.CURRENT_SCORE_FORMULA_VERSION)
                .dailyLimitReached(true)
                .build();
    }

    /**
     * 캐시 속 활동 목록을 DB와 대조해 비활성화(is_active=false)·삭제·마감(deadline < today)된
     * 항목을 제거한다. 마감일이 null(상시 모집)인 항목은 그대로 유지한다.
     * 활동은 최대 {@value #MAX_RECOMMENDABLE_ACTIVITIES}개뿐이라 findAllById 1회면 충분하다.
     * 캐시 JSON 자체는 다시 쓰지 않고(원본은 그대로 둔다), 읽을 때마다 필터링만 한다.
     */
    private RecommendationResponse filterStaleActivities(RecommendationResponse response, LocalDate today) {
        if (response == null || response.getActivities() == null || response.getActivities().isEmpty()) {
            return response;
        }

        List<UUID> ids = response.getActivities().stream()
                .map(ActivityRecommendation::getId)
                .filter(id -> id != null)
                .toList();
        Map<UUID, Activity> dbActivities = activityRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Activity::getId, a -> a));

        List<ActivityRecommendation> filtered = response.getActivities().stream()
                .filter(a -> isActivityUsable((a.getId() != null) ? dbActivities.get(a.getId()) : null, today))
                .toList();

        int removed = response.getActivities().size() - filtered.size();
        if (removed == 0) {
            return response;
        }
        log.info("추천 캐시 재검증: 비활성/마감/삭제된 활동 {}건 제거", removed);
        return response.toBuilder().activities(filtered).build();
    }

    /**
     * DB 활동이 지금 추천/로드맵에 노출해도 되는 상태인지 판단한다: 삭제되지 않았고
     * (db != null), 비활성화되지 않았고, 마감일이 없거나 아직 지나지 않았어야 한다.
     * filterStaleActivities와 mergeRoadmapActivities가 같은 기준을 쓴다.
     */
    private static boolean isActivityUsable(Activity db, LocalDate today) {
        if (db == null) return false; // DB에 없음(삭제됨)
        if (!Boolean.TRUE.equals(db.getIsActive())) return false; // 비활성화됨
        return db.getDeadline() == null || !db.getDeadline().isBefore(today); // 마감 지남
    }

    private boolean hasUsableCachedActivities(RecommendationResponse response, LocalDate today) {
        return response != null
                && response.getActivities() != null
                && !response.getActivities().isEmpty()
                && response.getActivities().stream()
                .noneMatch(activity -> activity.getDeadline() != null
                        && activity.getDeadline().isBefore(today));
    }

    private boolean isSpecModifiedSince(UserSpec userSpec, TargetJob targetJob, java.time.LocalDateTime cacheCreatedAt) {
        if (cacheCreatedAt == null) return true;
        if (userSpec != null && userSpec.getUpdatedAt() != null && userSpec.getUpdatedAt().isAfter(cacheCreatedAt)) {
            return true;
        }
        if (targetJob != null && targetJob.getUpdatedAt() != null && targetJob.getUpdatedAt().isAfter(cacheCreatedAt)) {
            return true;
        }
        return false;
    }

    /**
     * Gemini API를 호출하고 JSON 파싱을 시도한다. 실패 시 1회 재시도 후 Fallback 반환.
     */
    private RecommendationResponse callGeminiWithRetry(
            String userSpecJson, String targetJobStr, String positionContext, String feedbackContext,
            String availableActivitiesJson,
            SpecPositionResult position, List<Activity> activeActivities,
            String targetJobName, LocalDate today) {

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String rawJson = geminiService.generateRecommendation(
                        userSpecJson, targetJobStr, positionContext, feedbackContext, availableActivitiesJson, today);
                if (rawJson != null && !rawJson.isBlank()) {
                    RecommendationResponse res = parseGeminiResponse(
                            rawJson, position, activeActivities, targetJobName);
                    if (res != null) return res;
                }
            } catch (Exception e) {
                log.warn("Gemini 호출 또는 파싱 실패 (시도 {}/2): {}", attempt, e.getMessage());
            }
            if (attempt < 2) {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }

        // 최종 실패 시 Fallback 반환
        log.error("Gemini 추천 생성 모두 실패. DB 활동 기반 기본 추천 반환.");
        return buildFallbackResponse(activeActivities, position, targetJobName);
    }

    /**
     * Gemini 미사용/실패 시 DB 등록 활동 기반 기본 추천 반환 (방어 로직).
     *
     * ⚠️ 이 메서드는 절대 null을 반환하지 않는다. 호출부(getRecommendations)가 반환값에
     * 곧바로 isAiRecommendation()을 호출하므로, null을 주면 추천 API 전체가 NPE로 500이 된다.
     * 추천 가능한 활동이 0건인 상황(전 활동 마감·비활성화, 크롤링 중단 등)은 장애가 아니라
     * 정상적으로 발생할 수 있는 상태이므로, 활동 목록만 비운 응답을 만든다.
     * 위치·갭(specPosition)은 활동 유무와 무관하게 계산되므로 그대로 내려보낸다
     * (화면은 활동 0건을 "아직 추천할 활동이 없어요"로 이미 처리한다).
     */
    private RecommendationResponse buildFallbackResponse(List<Activity> activeActivities,
                                                         SpecPositionResult position, String targetJobName) {
        List<Activity> safeActivities = (activeActivities != null) ? activeActivities : List.of();

        // 예전엔 목록 앞 3개(마감 임박순)를 그대로 잘라 "왜 이 활동인지"가 없었다.
        // 갭 키워드·목표 직무 태그로 순위를 매기고, 갭이 맞는 활동엔 그 갭을 이유에 적는다.
        List<GapMatcher.Gap> knownGaps = GapMatcher.knownGaps(position);
        List<ActivityRecommendation> recs = new ArrayList<>();
        for (GapMatcher.Ranked r : GapMatcher.rankForFallback(safeActivities, targetJobName, knownGaps, 3)) {
            Activity a = r.activity();
            String reason;
            if (r.targetGap() != null) {
                reason = String.format("[AI 응답 지연 임시 추천] 합격자 비교에서 부족한 '%s'을(를) 보완할 수 있는 활동이에요.", r.targetGap());
            } else if (a.getDescription() != null && !a.getDescription().isBlank()) {
                reason = "[AI 응답 지연 임시 추천] " + a.getDescription();
            } else {
                reason = "[AI 응답 지연 임시 추천] 사용자의 목표 직무 및 학점 스펙 기반 DB 맞춤 추천 활동입니다.";
            }
            recs.add(ActivityRecommendation.builder()
                    .id(a.getId())
                    .type(a.getType())
                    .name(a.getName())
                    .reason(reason)
                    .deadline(a.getDeadline())
                    .targetGap(r.targetGap())
                    .build());
        }

        return RecommendationResponse.builder()
                .activities(recs)
                .specPosition(position)
                .targetJobName(targetJobName)
                .aiRecommendation(false)
                .scoreFormulaVersion(SpecPositionCalculator.CURRENT_SCORE_FORMULA_VERSION)
                .build();
    }

    /**
     * Gemini 응답 JSON을 RecommendationResponse DTO로 변환한다.
     * 타입 안전한 GeminiRecommendationResult DTO로 파싱하고,
     * DB에 실재하는 활동만 포함하며, specPosition을 주입한다.
     */
    private RecommendationResponse parseGeminiResponse(
            String rawJson, SpecPositionResult position,
            List<Activity> activeActivities, String targetJobName) throws Exception {

        // DB 활동을 UUID → Activity Map으로 변환 (빠른 검증용)
        Map<UUID, Activity> activityMap = new HashMap<>();
        for (Activity a : activeActivities) {
            activityMap.put(a.getId(), a);
        }

        GeminiRecommendationResult geminiResult = objectMapper.readValue(rawJson, GeminiRecommendationResult.class);

        if (geminiResult.getActivities() == null || geminiResult.getActivities().isEmpty()) return null;

        // Gemini의 targetGap은 비교 탭의 갭 이름과 같을 때만 받는다 — 지어낸 갭이 추천 카드에 뜨면 두 화면이 어긋난다.
        List<GapMatcher.Gap> knownGaps = GapMatcher.knownGaps(position);

        List<ActivityRecommendation> result = new ArrayList<>();
        for (GeminiActivity a : geminiResult.getActivities()) {
            // Gemini가 배열 원소로 null을 섞어 보내면(스키마 이탈) a.getId()에서 NPE가 나
            // 이 시도 전체가 버려진다 — 정상 원소가 섞여 있어도 배열 전체를 버리고 재시도
            // (2초 sleep + 최대 60초 Gemini 재호출)를 낭비하게 되므로, null 원소만 건너뛴다.
            if (a == null) continue;

            // Gemini가 반환한 ID를 UUID로 파싱
            UUID activityId = null;
            if (a.getId() != null) {
                try { activityId = UUID.fromString(a.getId()); } catch (Exception ignored) {}
            }

            // DB에 실재하는 활동만 포함 (할루시네이션 방지)
            if (activityId != null && activityMap.containsKey(activityId)) {
                Activity dbActivity = activityMap.get(activityId);
                result.add(ActivityRecommendation.builder()
                        .id(dbActivity.getId())
                        .type(dbActivity.getType())
                        .name(dbActivity.getName())
                        .reason(a.getReason() != null ? a.getReason() : "")
                        .deadline(dbActivity.getDeadline())
                        .targetGap(GapMatcher.normalizeTargetGap(a.getTargetGap(), knownGaps)
                                .or(() -> GapMatcher.matchGap(dbActivity, knownGaps))
                                .orElse(null))
                        .build());
            } else {
                log.warn("Gemini가 DB에 없는 활동 ID를 반환함 (무시): {}", a.getId());
            }
        }

        if (result.isEmpty()) return null;

        return RecommendationResponse.builder()
                .activities(result)
                .specPosition(position)
                .targetJobName(targetJobName)
                .aiRecommendation(true)
                .scoreFormulaVersion(SpecPositionCalculator.CURRENT_SCORE_FORMULA_VERSION)
                .build();
    }

    private RecommendationResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, RecommendationResponse.class);
        } catch (Exception e) {
            log.warn("캐시 역직렬화 실패 → 재생성: {}", e.getMessage());
            return null;
        }
    }
}
