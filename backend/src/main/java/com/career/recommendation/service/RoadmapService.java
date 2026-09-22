package com.career.recommendation.service;

import com.career.recommendation.util.ServiceTime;
import com.career.recommendation.dto.gemini.GeminiRoadmapResult;
import com.career.recommendation.dto.gemini.GeminiRoadmapResult.GeminiTimelineStep;
import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.dto.roadmap.RoadmapResponse;
import com.career.recommendation.dto.roadmap.RoadmapResponse.MatchedActivity;
import com.career.recommendation.dto.roadmap.RoadmapResponse.TimelineStep;
import com.career.recommendation.dto.position.SpecPositionResult;
import com.career.recommendation.entity.Activity;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.User;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.entity.RoadmapCache;
import com.career.recommendation.entity.Recommendation;
import com.career.recommendation.repository.ActivityRepository;
import com.career.recommendation.repository.TargetJobRepository;
import com.career.recommendation.repository.UserSpecRepository;
import com.career.recommendation.repository.RoadmapCacheRepository;
import com.career.recommendation.repository.RecommendationRepository;
import com.career.recommendation.util.GraduateOnlyActivityFilter;
import com.career.recommendation.util.PromptDataBuilder;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * BE-1 담당 — F-05 커리어 로드맵 비즈니스 로직.
 * 유저 학년을 기반으로 학기/방학 단위로 구분된 12개월 타임라인을 생성한다(2026-09-17 실사용
 * 피드백으로 6개월 → 12개월 확장).
 *
 * RAG 패턴 적용 — DB 활동 목록을 Gemini 프롬프트에 주입하여
 * AI가 실제 존재하는 활동 중에서만 선택하도록 하고, 응답 ID를 DB와 검증하여 할루시네이션을 방지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoadmapService {

    private final CurrentUserService currentUserService;
    private final UserSpecRepository userSpecRepository;
    private final TargetJobRepository targetJobRepository;
    private final ActivityRepository activityRepository;
    private final SpecPositionService specPositionService;
    private final RecommendationRepository recommendationRepository;
    private final RoadmapCacheRepository roadmapCacheRepository;
    private final RoadmapCacheService roadmapCacheService;
    private final GeminiService geminiService;
    private final PromptDataBuilder promptDataBuilder;
    private final AiDailyAttemptLimiter aiDailyAttemptLimiter;
    private final ObjectMapper objectMapper;

    private static final int MAX_RECOMMENDABLE_ACTIVITIES = 20;
    private static final ZoneId SERVICE_ZONE_ID = ServiceTime.ZONE_ID;
    /** 프롬프트가 HIGH/MEDIUM/LOW만 쓰라고 지시하지만 강제되지 않아, Gemini가 임의 문자열을
     * 반환해도 검증 없이 그대로 FE에 전달되고 있었다. FE가 이 값으로 배지를 매핑한다면
     * 미매핑 값에서 빈 배지가 뜬다. */
    private static final Set<String> VALID_PRIORITIES = Set.of("HIGH", "MEDIUM", "LOW");

    /**
     * 현재 로그인한 유저의 12개월 커리어 로드맵을 반환한다.
     * F-03 맞춤 추천 활동 및 유사 합격자 데이터를 공유받아 일관성 있는 로드맵을 생성한다.
     */
    public RoadmapResponse getRoadmap(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);

        // 1. 캐시 확인 및 스펙 변경 여부 판별 (추천과 동일한 일일 3회 제한 정책)
        RoadmapCache cached = roadmapCacheRepository.findByUser_Id(user.getId()).orElse(null);
        UserSpec userSpec   = userSpecRepository.findByUser_Id(user.getId()).orElse(null);
        TargetJob targetJob = targetJobRepository.findByUser_Id(user.getId()).orElse(null);
        LocalDate today = LocalDate.now(SERVICE_ZONE_ID);
        boolean attemptAcquired = false;

        if (cached != null) {
            try {
                RoadmapResponse deserialized = objectMapper.readValue(cached.getResultJson(), RoadmapResponse.class);
                if (deserialized != null && deserialized.getTimeline() != null && !deserialized.getTimeline().isEmpty()) {
                    // 캐시는 스펙이 바뀔 때만 재생성되는데, 생성 후 스케줄러가 마감 활동을
                    // 비활성화하거나 관리자가 활동을 내리면 캐시가 그 활동을 로드맵 스텝에
                    // 계속 노출한다(RecommendationService.filterStaleActivities와 같은 문제).
                    // 스텝 자체는 유지하고, 스텝 안의 matchedActivities만 DB와 대조해 걸러낸다.
                    // ⚠️ 필터링 전 "원래 매칭이 있었는지"를 먼저 기록해 둔다 — 실사용 확인: 매칭
                    // 활동이 전부 마감·비활성화돼 필터 후 모든 스텝이 0건이 되어도(추천은 이 경우
                    // 재생성이 발동하는데) 로드맵은 스텝을 유지한 채 영원히 낡은 채로 남아 있었다.
                    // 반대로 원래부터 매칭이 하나도 없던 캐시(activity 텍스트만 있는 스텝)까지
                    // 매번 재생성 대상으로 잡으면 불필요한 Gemini 재호출만 늘어난다 — 두 경우를
                    // 구분하려면 필터 "전" 상태를 따로 봐야 한다.
                    boolean originallyHadMatchedActivities = hasAnyMatchedActivities(deserialized);
                    deserialized = filterStaleMatchedActivities(deserialized, today);
                    boolean isSpecChanged = isSpecModifiedSince(userSpec, targetJob, cached.getCreatedAt());
                    // 마감이 지난 활동이 캐시에 남아 있으면 스펙이 그대로여도 다시 만든다.
                    // 그러지 않으면 이미 마감된 활동을 로드맵에 무기한 보여주게 된다. 단, 원래부터
                    // 매칭 활동이 없던 캐시는 "필터 후 0건"이 항상 참이라 이 규칙에서 제외한다
                    // (불필요 재생성 방지 — hasAnyMatchedActivities 주석 참고).
                    boolean invalidatedByFilter = originallyHadMatchedActivities && !hasUsableCachedActivities(deserialized, today);
                    boolean hasUsableActivities = !invalidatedByFilter;

                    if (!isSpecChanged && hasUsableActivities) {
                        return deserialized;
                    }
                    // 갱신이 필요하더라도 하루 시도 상한(AiDailyAttemptLimiter — 성공·실패 무관)을 넘으면 캐시를 그대로 준다.
                    // 예전 게이트(daily_update_count)는 저장 성공만 세서 Gemini 장애 중엔 발동하지 않았다.
                    if (!aiDailyAttemptLimiter.tryAcquire(user.getId(), AiDailyAttemptLimiter.KIND_ROADMAP)) {
                        // 한도에 막혀 옛 로드맵을 주는 것임을 FE가 안내할 수 있게 플래그를
                        // 붙인다. toBuilder 결과는 반환 전용 — 캐시에 저장하지 않는다
                        // (RecommendationResponse.dailyLimitReached 주석 참고).
                        return deserialized.toBuilder().dailyLimitReached(true).build();
                    }
                    attemptAcquired = true;
                }
            } catch (Exception e) {
                log.warn("로드맵 캐시 파싱 실패: {}", e.getMessage());
            }
        }
        // 캐시가 없거나 비었거나 깨진 경로도 같은 상한을 지난다 — 예전엔 이 세 경로가 게이트를 우회해 곧장 Gemini로 갔다.
        if (!attemptAcquired && !aiDailyAttemptLimiter.tryAcquire(user.getId(), AiDailyAttemptLimiter.KIND_ROADMAP)) {
            List<Activity> openForFallback = activityRepository.findRecommendableActivities(today, PageRequest.of(0, MAX_RECOMMENDABLE_ACTIVITIES));
            Integer gradeForFallback = (userSpec != null) ? userSpec.getGrade() : null;
            openForFallback = GraduateOnlyActivityFilter.filterForGrade(openForFallback, gradeForFallback);
            return buildFallbackRoadmap(gradeForFallback, openForFallback, today).toBuilder().dailyLimitReached(true).build();
        }

        String userSpecJson = promptDataBuilder.serializeSpecForRoadmap(userSpec);
        String targetJobStr = promptDataBuilder.buildTargetJobString(targetJob);
        Integer grade       = (userSpec != null) ? userSpec.getGrade() : null;

        // 1. 합격자 비교 데이터(직무 요구 프로필 내 위치·갭) 계산 — F-03과 같은 진입점
        // (SpecPositionService)을 써서 추천과 로드맵이 같은 갭을 보고 말하게 한다.
        String jobType = (targetJob != null) ? targetJob.getJobType() : null;
        SpecPositionResult position = specPositionService.calculate(userSpec, jobType);
        String positionContextStr = promptDataBuilder.buildPositionContextText(position);

        // 2. F-03 맞춤 추천 결과 조회 (DB 캐시만 참조하여 Gemini 중복 API 호출 방지)
        //
        // ⚠️ 이 캐시를 RecommendationService를 거치지 않고 여기서 직접 읽는다 — RecommendationService의
        // legacy 판정·신선도 검사(hasUsableCachedActivities 등)를 전혀 통과하지 않은 값이라는
        // 뜻이다. 마감 필터링 없이 그대로 프롬프트에 "우선 반영할 활동"으로 주입하면(로드맵
        // 프롬프트 규칙 3), 이미 마감된 활동이 로드맵 스텝에 이름으로만 박힐 수 있다 — 그 활동
        // ID는 findRecommendableActivities()의 마감일 필터에 안 걸려 matchedActivities에서는
        // 빠지지만 activity 텍스트에는 남고, hasUsableCachedActivities()는 matchedActivities만
        // 보므로 이 로드맵은 "사용 가능"으로 영구 캐시된다(빈 로드맵 영구 캐싱과 같은 형태의
        // 자가회복 불가 상태). 마감 지난 활동은 여기서 미리 걸러낸다.
        String topRecommendedJson = "[]";
        Set<UUID> topRecommendedIds = Set.of();
        try {
            Recommendation cachedRec = recommendationRepository.findByUser_Id(user.getId()).orElse(null);
            if (cachedRec != null && cachedRec.getResultJson() != null) {
                RecommendationResponse recResponse = objectMapper.readValue(cachedRec.getResultJson(), RecommendationResponse.class);
                if (recResponse != null && recResponse.getActivities() != null) {
                    List<RecommendationResponse.ActivityRecommendation> stillOpen = recResponse.getActivities().stream()
                            .filter(a -> a.getDeadline() == null || !a.getDeadline().isBefore(today))
                            .toList();
                    topRecommendedJson = objectMapper.writeValueAsString(stillOpen);
                    // [전체 DB 등록 활동 목록]에서 제외할 ID — 이미 [우선 반영할 AI 추천 활동]에
                    // 실린 활동을 두 목록에 중복으로 넣으면 프롬프트 토큰만 낭비한다.
                    topRecommendedIds = stillOpen.stream()
                            .map(RecommendationResponse.ActivityRecommendation::getId)
                            .filter(id -> id != null)
                            .collect(Collectors.toSet());
                }
            }
        } catch (Exception e) {
            log.warn("F-03 추천 캐시 조회 중 오류 (기본값 [] 사용): {}", e.getMessage());
        }

        // 3. 현재 신청 가능한 DB 활동 조회 (RAG 패턴)
        // today를 위(82행)에서 이미 계산한 값과 동일하게 재사용한다 — 따로 다시
        // LocalDate.now()를 부르면 자정 경계를 걸쳐 실행될 때 바로 위 topRecommendedJson
        // 마감 필터(153행)와 하루 다른 기준일로 어긋날 수 있다.
        List<Activity> activeActivities = activityRepository.findRecommendableActivities(
                today,
                PageRequest.of(0, MAX_RECOMMENDABLE_ACTIVITIES)
        );
        // 재학생(1~3학년)은 "학사 학위 이상/졸업예정자 전용" 대졸 공채에 지원할 수 없다 — 후보 선정
        // 단계에서 미리 제외한다(추천과 공통 헬퍼, GraduateOnlyActivityFilter 참고).
        activeActivities = GraduateOnlyActivityFilter.filterForGrade(activeActivities, grade);
        String availableActivitiesJson = promptDataBuilder.buildAvailableActivitiesJsonForRoadmap(activeActivities, topRecommendedIds);

        // 4. Gemini API 호출 (최대 2회 시도)
        RoadmapResponse response = callGeminiWithRetry(userSpecJson, targetJobStr, grade,
                positionContextStr, topRecommendedJson, availableActivitiesJson, activeActivities, today);

        if (response.isAiRoadmap()) {
            roadmapCacheService.save(user, response);
        }
        
        return response;
    }

    private RoadmapResponse callGeminiWithRetry(String userSpecJson, String targetJobStr, Integer grade,
                                                 String positionContextStr, String topRecommendedJson,
                                                 String availableActivitiesJson, List<Activity> activeActivities,
                                                 LocalDate today) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String rawJson = geminiService.generateRoadmap(
                        userSpecJson, targetJobStr, grade,
                        positionContextStr, topRecommendedJson, availableActivitiesJson, today);
                if (rawJson == null || rawJson.isBlank()) {
                    log.warn("Gemini 로드맵 응답 비어있음 (시도 {}회)", attempt);
                } else {
                    RoadmapResponse parsed = parseGeminiResponse(rawJson, activeActivities);
                    if (parsed != null) return parsed;
                }
            } catch (Exception e) {
                log.warn("Gemini 로드맵 파싱 실패 (시도 {}회): {}", attempt, e.getMessage());
            }
            // ⚠️ 예전엔 rawJson.isBlank()일 때 continue로 곧장 다음 반복으로 넘어가,
            // 아래 백오프(2초 sleep)를 건너뛰고 즉시 재호출했다. Gemini가 과부하·레이트리밋으로
            // 빈 응답을 주는 상황에서 백오프 없이 바로 재시도하면 오히려 상황을 악화시킨다.
            // if/else로 바꿔 모든 실패 경로가 이 sleep을 반드시 거치게 했다.
            if (attempt < 2) {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
        log.info("Gemini 로드맵 미사용/실패 → DB 저장 활동 기반 로드맵 반환");
        return buildFallbackRoadmap(grade, activeActivities, today);
    }

    /**
     * Gemini 응답 JSON을 RoadmapResponse DTO로 변환한다.
     * 타입 안전한 GeminiRoadmapResult DTO로 파싱하고, DB와 대조하여 실재하는 활동만 포함한다.
     */
    private RoadmapResponse parseGeminiResponse(String rawJson,
                                                 List<Activity> activeActivities) throws Exception {
        // DB 활동을 UUID → Activity Map으로 변환 (빠른 검증용)
        Map<UUID, Activity> activityMap = new HashMap<>();
        for (Activity a : activeActivities) {
            activityMap.put(a.getId(), a);
        }

        // 타입 안전한 DTO로 파싱 (개선 #5)
        GeminiRoadmapResult geminiResult = objectMapper.readValue(rawJson, GeminiRoadmapResult.class);
        if (geminiResult.getTimeline() == null || geminiResult.getTimeline().isEmpty()) return null;

        List<TimelineStep> steps = new ArrayList<>();
        for (GeminiTimelineStep t : geminiResult.getTimeline()) {
            // Gemini가 배열 원소로 null을 섞어 보내면(스키마 이탈) t.getPeriod() 등에서
            // NPE가 나 이 시도 전체가 버려진다 — 재시도 1회(2초 sleep + 최대 60초 Gemini
            // 호출)를 그냥 낭비하는 셈이라, null 원소만 건너뛰고 나머지는 살린다.
            if (t == null) continue;

            String period = t.getPeriod() != null ? t.getPeriod() : "";

            // Gemini가 반환한 activityIds에서 DB에 실재하는 활동만 매칭
            List<MatchedActivity> matched = new ArrayList<>();
            if (t.getActivityIds() != null) {
                for (String idStr : t.getActivityIds()) {
                    try {
                        UUID activityId = UUID.fromString(idStr);
                        if (activityMap.containsKey(activityId)) {
                            matched.add(toMatchedActivity(activityMap.get(activityId)));
                        } else {
                            log.warn("Gemini 로드맵이 DB에 없는 활동 ID를 반환함 (무시): {}", idStr);
                        }
                    } catch (Exception ignored) {
                        log.warn("Gemini 로드맵이 잘못된 형식의 ID를 반환함 (무시): {}", idStr);
                    }
                }
            }

            String rawActivity = t.getActivity();
            String activityText;
            if (rawActivity != null && !rawActivity.isBlank() && !"null".equalsIgnoreCase(rawActivity)) {
                activityText = rawActivity;
            } else {
                activityText = matched.stream().map(MatchedActivity::getName).reduce((a, b) -> a + ", " + b).orElse("");
            }

            // ⚠️ 알맹이(활동 텍스트도, 매칭된 실제 DB 활동도) 하나도 없는 스텝은 버린다.
            // 예전엔 스텝 "개수"만 보고(steps.isEmpty()) "AI 성공"으로 판정했는데, Gemini가
            // {"timeline":[{},{},{}]}처럼 형태만 갖추고 내용이 빈 응답을 주면 activity=''·
            // matchedActivities=[]인 스텝 여러 개가 그대로 통과해 aiRoadmap=true로 캐시에
            // 영구 저장됐다. 이 캐시는 스스로 회복되지 않는다 — hasUsableCachedActivities()가
            // matchedActivities를 flatMap해서 마감일을 보는데 전부 비어있으니 noneMatch가
            // 항상 true가 되어 "사용 가능"으로 판정되고, 유저가 스펙을 다시 저장하기 전까지
            // 빈 로드맵이 영구히 반환된다.
            if (activityText.isBlank() && matched.isEmpty()) continue;

            steps.add(TimelineStep.builder()
                    .period(period)
                    .priority(normalizePriority(t.getPriority()))
                    .activity(activityText)
                    .reason(t.getReason() != null ? t.getReason() : "")
                    .matchedActivities(matched)
                    .build());
        }

        if (steps.isEmpty()) return null;

        return RoadmapResponse.builder().timeline(steps).build();
    }

    /**
     * ⚠️ Set.of(...).contains(null)은 false가 아니라 NullPointerException을 던진다
     * (Objects.requireNonNull 기반 구현). VALID_PRIORITIES.contains(t.getPriority())로
     * 직접 검사했을 때, Gemini가 priority 필드 하나만 빠뜨려도(스키마 자체는 정상,
     * responseMimeType=application/json이 필드 "존재"까지 보장하진 않는다) 그 NPE가
     * parseGeminiResponse 밖으로 튀어 callGeminiWithRetry의 catch(Exception)에 잡히고,
     * 완전히 정상적인 나머지 스텝까지 전부 "파싱 실패"로 버려진 채 2회 재시도(+2초 백오프,
     * 최대 122초)를 다 태우고 폴백으로 떨어졌다 — 예전(null 삼항 체크)엔 없던, 이번
     * 화이트리스트 도입이 만든 회귀다. null을 먼저 걸러내고, 대소문자도 함께 정규화한다
     * (ActivityController의 direction 파라미터와 같은 이유 — Gemini가 "high"처럼 소문자를
     * 줘도 대문자 값만 허용하던 화이트리스트가 조용히 전부 MEDIUM으로 뭉갰다).
     */
    private String normalizePriority(String raw) {
        if (raw == null) return "MEDIUM";
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return VALID_PRIORITIES.contains(normalized) ? normalized : "MEDIUM";
    }

    private MatchedActivity toMatchedActivity(Activity activity) {
        return MatchedActivity.builder()
                .activityId(activity.getId())
                .name(activity.getName())
                .type(activity.getType())
                .organization(activity.getOrganization())
                .deadline(activity.getDeadline())
                .url(activity.getUrl())
                .build();
    }

    // 폴백 로드맵의 시기별 가이드 문구. 인덱스 0이 가장 이른(HIGH) 시기, 이후 순서대로 이어진다.
    // 2026-09-17 실사용 피드백으로 로드맵을 6개월(3구간)에서 12개월(학년 있음: 4구간, 없음: 6구간)로
    // 넓히면서, 예전엔 3개뿐이던 가이드 문구를 그 이후 시기까지 채우도록 늘렸다.
    private static final String[] FALLBACK_GUIDE_ACTIVITY = {
            "[AI 응답 지연] 핵심 SW 교육 및 인턴십 지원",
            "[AI 응답 지연] 부트캠프 및 프로젝트 몰입",
            "[AI 응답 지연] 오픈소스 기여 및 해커톤 공모전 참가",
            "[AI 응답 지연] 심화 프로젝트 및 기술 스택 확장",
            "[AI 응답 지연] 채용 연계형 인턴십 및 공채 서류 준비",
            "[AI 응답 지연] 최종 코딩테스트·면접 대비",
    };
    private static final String[] FALLBACK_GUIDE_REASON = {
            "[서버 지연 임시 로드맵] 서류 가점 및 기초 실무 역량을 다지는 핵심 시기입니다.",
            // ⚠️ "방학 기간을 활용하여"처럼 계절을 못박은 문구는 쓰지 않는다 — computeFallbackPeriods가
            // today 기준으로 학기/방학을 동적으로 계산하므로, 이 인덱스가 방학이 아니라 학기(2학기·1학기)인
            // 경우도 생긴다(예: 7~8월 요청 → 2번째 시기가 "2학기"). 방학이라고 단정하는 문구가 남아 있으면
            // 그 경우 카드 본문과 기간 라벨이 서로 모순된다.
            "[서버 지연 임시 로드맵] 이 시기를 활용하여 포트폴리오를 대폭 강화합니다.",
            "[서버 지연 임시 로드맵] 실무 협업 역량을 입증하고 채용 우대 혜택을 획득합니다.",
            "[서버 지연 임시 로드맵] 지금까지 쌓은 역량을 심화하고 목표 직무에 필요한 기술을 추가로 학습합니다.",
            "[서버 지연 임시 로드맵] 본격적인 채용 시즌에 대비해 서류·포트폴리오를 완성도 있게 정리합니다.",
            "[서버 지연 임시 로드맵] 목표 기업의 채용 전형에 맞춰 실전 감각을 끌어올립니다.",
    };

    /**
     * Gemini 미사용/실패 시 DB 등록 활동 기반 기본 로드맵 반환.
     *
     * ⚠️ 예전엔 이 시기 라벨이 "N학년 2학기 (9~11월)"부터 시작하도록 하드코딩돼 있었다 —
     * 요청 시점이 실제로 몇 월인지는 전혀 보지 않았다. today를 기준으로 현재 속한 학기/방학
     * 구간부터 순서대로 계산한다(computeFallbackPeriods).
     */
    private RoadmapResponse buildFallbackRoadmap(Integer grade, List<Activity> activeActivities, LocalDate today) {
        String[] periods = computeFallbackPeriods(grade, today);

        // ⚠️ 예전엔 활동을 리스트 인덱스로만 등분해(예: 0~2→1번째, 3~5→2번째) 각 스텝에
        // 나눠 담았다. activeActivities는 findRecommendableActivities가 deadline ASC로 정렬해
        // 준 목록이라, 시기 라벨이 today 기준 실제 달력 구간이 되면서 예를 들어 8월에 마감하는
        // 활동이 "3학년 겨울방학 (12~2월)" 밑에 나오는 것처럼 카드에 적힌 matchedActivities의
        // 마감일과 그 시기 라벨이 정면으로 모순될 수 있다. 시기별 실제 마감일 구간을 계산해
        // 재배정하는 대신(이번 마감 전 범위를 넘는 작업), 첫 번째(HIGH="지금 집중") 시기에만
        // 실제 DB 활동을 붙이고 나머지는 텍스트 가이드만 보여준다 — 이러면 최소한 "틀린 날짜
        // 주장"은 절대 나오지 않는다.
        List<MatchedActivity> firstStepMatched = new ArrayList<>();
        if (activeActivities != null) {
            for (int i = 0; i < activeActivities.size() && i < 3; i++) {
                firstStepMatched.add(toMatchedActivity(activeActivities.get(i)));
            }
        }

        List<TimelineStep> steps = new ArrayList<>();
        for (int i = 0; i < periods.length; i++) {
            // priority 규칙은 Gemini 프롬프트 규칙 8과 동일하다: 가장 이른 시기만 HIGH, 그
            // 다음은 MEDIUM, 이후 나머지 전부는 LOW.
            String priority = (i == 0) ? "HIGH" : (i == 1) ? "MEDIUM" : "LOW";
            steps.add(TimelineStep.builder()
                    .period(periods[i])
                    .priority(priority)
                    .activity(FALLBACK_GUIDE_ACTIVITY[i % FALLBACK_GUIDE_ACTIVITY.length])
                    .reason(FALLBACK_GUIDE_REASON[i % FALLBACK_GUIDE_REASON.length])
                    .matchedActivities(i == 0 ? firstStepMatched : List.of())
                    .build());
        }

        return RoadmapResponse.builder()
                .timeline(steps)
                .aiRoadmap(false)
                .build();
    }

    /**
     * grade가 있을 때, today가 속한 학기/방학 구간부터 순서대로 4개의 시기 라벨을 만든다.
     * 한 해를 1학기(3~6월, 4개월)·여름방학(7~8월, 2개월)·2학기(9~11월, 3개월)·겨울방학
     * (12~2월, 3개월) 4구간으로 보는데, 이 4종류를 정확히 한 바퀴(4구간) 돌면 시작 시점과
     * 무관하게 항상 4+2+3+3=12개월이 된다 — "12개월 로드맵" 계약과 정확히 맞아떨어지는
     * 구간 수라 3이 아닌 4를 쓴다(6개월 로드맵이던 시절엔 3구간을 썼다). 겨울방학 다음엔
     * 학년이 하나 올라간다. 4학년을 넘어가면 "졸업 후 취업 준비"로 고정한다.
     * grade가 null이면 학기 개념이 없으므로 상대적인 "N~N개월 차" 라벨을 2개월 단위로
     * 6구간(12개월) 채워 그대로 쓴다.
     */
    private String[] computeFallbackPeriods(Integer grade, LocalDate today) {
        if (grade == null) {
            return new String[]{
                    "1~2개월 차", "3~4개월 차", "5~6개월 차", "7~8개월 차", "9~10개월 차", "11~12개월 차",
            };
        }
        int month = today.getMonthValue();
        int termIndex; // 0=1학기, 1=여름방학, 2=2학기, 3=겨울방학
        if (month >= 3 && month <= 6) termIndex = 0;
        else if (month >= 7 && month <= 8) termIndex = 1;
        else if (month >= 9 && month <= 11) termIndex = 2;
        else termIndex = 3;

        String[] periods = new String[4];
        int curGrade = grade;
        int curTerm = termIndex;
        for (int i = 0; i < periods.length; i++) {
            periods[i] = fallbackTermLabel(curGrade, curTerm);
            curTerm++;
            if (curTerm > 3) {
                curTerm = 0;
                curGrade++;
            }
        }
        return periods;
    }

    private String fallbackTermLabel(int grade, int termIndex) {
        if (grade > 4) {
            return "졸업 후 취업 준비";
        }
        return switch (termIndex) {
            case 0 -> grade + "학년 1학기 (3~6월)";
            case 1 -> grade + "학년 여름방학 (7~8월)";
            case 2 -> grade + "학년 2학기 (9~11월)";
            default -> grade + "학년 겨울방학 (12~2월)";
        };
    }

    /**
     * 캐시된 로드맵에 붙어 있는 실제 DB 활동(matchedActivities)이 아직 유효한지 확인한다.
     * 마감이 지난 활동이 하나라도 있으면 로드맵을 다시 생성해야 한다.
     * (RecommendationService.hasUsableCachedActivities와 같은 목적)
     *
     * ⚠️ RecommendationService 쪽은 !activities.isEmpty()를 먼저 확인하는데, 이쪽엔 그
     * 빈-목록 가드가 없었다. matchedActivities가 모든 스텝에서 전부 비어 있으면
     * flatMap 결과가 빈 스트림이 되고, 빈 스트림에 대한 noneMatch는 항상 true를 반환한다
     * (vacuous truth) — 즉 "사용 가능"으로 잘못 판정된다. DB에 신청 가능한 활동이 아직
     * 하나도 없을 때(크롤러 미작동·전량 마감 등) Gemini가 activity 텍스트만 있고
     * activityIds는 비거나 유효하지 않은 응답을 주면, parseGeminiResponse는 이 스텝을
     * 버리지 않고(텍스트가 있으므로) aiRoadmap=true로 저장한다. 다음날 크롤러가 활동을
     * 채워 넣어도 이 캐시는 "사용 가능"으로 영구 판정돼, 유저가 스펙을 다시 저장하기
     * 전까지 실제 DB 활동을 절대 반영하지 못한다 — 위(263~270행) 완전-빈-스텝 케이스와
     * 같은 자가회복 불가 상태가 "텍스트는 있지만 매칭된 활동이 하나도 없는" 케이스에도
     * 똑같이 존재했다.
     */
    /**
     * 캐시된 로드맵의 각 스텝 matchedActivities를 DB와 대조해 비활성화(is_active=false)·
     * 삭제·마감(deadline < today)된 활동을 제거한다. 마감일이 null(상시 모집)인 활동은
     * 유지한다. 스텝 자체(period·activity 텍스트·reason)는 그대로 두고 matchedActivities만
     * 걸러낸다 — 전부 제거돼 빈 배열이 되어도 스텝은 남는다.
     * 타임라인은 최대 3스텝·스텝당 소수의 활동이라 전체 distinct id에 대해 findAllById 1회면 충분하다.
     */
    private RoadmapResponse filterStaleMatchedActivities(RoadmapResponse response, LocalDate today) {
        if (response == null || response.getTimeline() == null || response.getTimeline().isEmpty()) {
            return response;
        }

        List<UUID> ids = response.getTimeline().stream()
                .filter(step -> step != null && step.getMatchedActivities() != null)
                .flatMap(step -> step.getMatchedActivities().stream())
                .map(MatchedActivity::getActivityId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return response;
        }

        Map<UUID, Activity> dbActivities = activityRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Activity::getId, a -> a));

        int[] removed = {0};
        List<TimelineStep> filteredSteps = response.getTimeline().stream()
                .map(step -> {
                    if (step == null || step.getMatchedActivities() == null || step.getMatchedActivities().isEmpty()) {
                        return step;
                    }
                    List<MatchedActivity> filtered = step.getMatchedActivities().stream()
                            .filter(ma -> {
                                Activity db = (ma.getActivityId() != null) ? dbActivities.get(ma.getActivityId()) : null;
                                if (db == null) return false; // DB에 없음(삭제됨)
                                if (!Boolean.TRUE.equals(db.getIsActive())) return false; // 비활성화됨
                                return db.getDeadline() == null || !db.getDeadline().isBefore(today); // 마감 지남
                            })
                            .toList();
                    if (filtered.size() == step.getMatchedActivities().size()) {
                        return step;
                    }
                    removed[0] += step.getMatchedActivities().size() - filtered.size();
                    return TimelineStep.builder()
                            .period(step.getPeriod())
                            .priority(step.getPriority())
                            .activity(step.getActivity())
                            .reason(step.getReason())
                            .matchedActivities(filtered)
                            .build();
                })
                .toList();

        if (removed[0] == 0) {
            return response;
        }
        log.info("로드맵 캐시 재검증: 비활성/마감/삭제된 활동 {}건 제거", removed[0]);
        return response.toBuilder().timeline(filteredSteps).build();
    }

    /**
     * 스텝 어딘가에 matchedActivities가 하나라도 있는지만 본다(마감일 등 신선도는 보지 않음).
     * "필터링 전 원래 매칭이 있었는가"를 판단하는 용도 — hasUsableCachedActivities와 달리 빈
     * 목록/전부 빈 스텝이면 그냥 false를 반환하면 되므로 vacuous truth 문제가 없다.
     */
    private boolean hasAnyMatchedActivities(RoadmapResponse response) {
        if (response == null || response.getTimeline() == null) {
            return false;
        }
        return response.getTimeline().stream()
                .filter(step -> step != null && step.getMatchedActivities() != null)
                .anyMatch(step -> !step.getMatchedActivities().isEmpty());
    }

    private boolean hasUsableCachedActivities(RoadmapResponse response, LocalDate today) {
        if (response == null || response.getTimeline() == null) {
            return false;
        }
        List<MatchedActivity> allMatched = response.getTimeline().stream()
                .filter(step -> step != null && step.getMatchedActivities() != null)
                .flatMap(step -> step.getMatchedActivities().stream())
                .toList();
        return !allMatched.isEmpty()
                && allMatched.stream().noneMatch(activity -> activity.getDeadline() != null
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
}
