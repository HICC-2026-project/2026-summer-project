package com.career.recommendation.service;

import com.career.recommendation.dto.gemini.GeminiRoadmapResult;
import com.career.recommendation.dto.gemini.GeminiRoadmapResult.GeminiTimelineStep;
import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.dto.roadmap.RoadmapResponse;
import com.career.recommendation.dto.roadmap.RoadmapResponse.MatchedActivity;
import com.career.recommendation.dto.roadmap.RoadmapResponse.TimelineStep;
import com.career.recommendation.entity.Activity;
import com.career.recommendation.entity.PasserData;
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
import com.career.recommendation.util.PromptDataBuilder;
import com.career.recommendation.util.SimilarSpecFinder;
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

/**
 * BE-1 담당 — F-05 커리어 로드맵 비즈니스 로직.
 * 유저 학년을 기반으로 학기/방학 단위로 구분된 6개월 타임라인을 생성한다.
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
    private final SimilarSpecFinder similarSpecFinder;
    private final RecommendationRepository recommendationRepository;
    private final RoadmapCacheRepository roadmapCacheRepository;
    private final RoadmapCacheService roadmapCacheService;
    private final GeminiService geminiService;
    private final PromptDataBuilder promptDataBuilder;
    private final ObjectMapper objectMapper;

    private static final int MAX_RECOMMENDABLE_ACTIVITIES = 20;
    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");
    /** 프롬프트가 HIGH/MEDIUM/LOW만 쓰라고 지시하지만 강제되지 않아, Gemini가 임의 문자열을
     * 반환해도 검증 없이 그대로 FE에 전달되고 있었다. FE가 이 값으로 배지를 매핑한다면
     * 미매핑 값에서 빈 배지가 뜬다. */
    private static final Set<String> VALID_PRIORITIES = Set.of("HIGH", "MEDIUM", "LOW");

    /**
     * 현재 로그인한 유저의 6개월 커리어 로드맵을 반환한다.
     * F-03 맞춤 추천 활동 및 유사 합격자 데이터를 공유받아 일관성 있는 로드맵을 생성한다.
     */
    public RoadmapResponse getRoadmap(Authentication authentication) {
        User user = currentUserService.getCurrentUser(authentication);

        // 1. 캐시 확인 및 스펙 변경 여부 판별 (추천과 동일한 일일 3회 제한 정책)
        RoadmapCache cached = roadmapCacheRepository.findByUser_Id(user.getId()).orElse(null);
        UserSpec userSpec   = userSpecRepository.findByUser_Id(user.getId()).orElse(null);
        TargetJob targetJob = targetJobRepository.findByUser_Id(user.getId()).orElse(null);
        LocalDate today = LocalDate.now(SERVICE_ZONE_ID);

        if (cached != null) {
            try {
                RoadmapResponse deserialized = objectMapper.readValue(cached.getResultJson(), RoadmapResponse.class);
                if (deserialized != null && deserialized.getTimeline() != null && !deserialized.getTimeline().isEmpty()) {
                    boolean isSpecChanged = isSpecModifiedSince(userSpec, targetJob, cached.getCreatedAt());
                    // 마감이 지난 활동이 캐시에 남아 있으면 스펙이 그대로여도 다시 만든다.
                    // 그러지 않으면 이미 마감된 활동을 로드맵에 무기한 보여주게 된다.
                    boolean hasUsableActivities = hasUsableCachedActivities(deserialized, today);

                    if (!isSpecChanged && hasUsableActivities) {
                        return deserialized;
                    }
                    // 갱신이 필요하더라도 하루 제한(3회)을 넘으면 캐시를 그대로 준다.
                    // 만료 활동이 계속 남아 있거나 Gemini가 실패를 반복할 때
                    // 매 요청마다 API를 호출하는 것을 막는 안전장치다.
                    //
                    // ⚠️ 알려진 한계(RecommendationService와 동일 — 그쪽 주석 참고): dailyUpdateCount는
                    // roadmapCacheService.save()가 실제로 실행됐을 때만(즉 response.isAiRoadmap()
                    // ==true, 성공했을 때만) 증가한다. Gemini가 계속 실패해 폴백만 반복되면 카운트가
                    // 0에 머물러 이 게이트가 절대 발동하지 않는다 — "실패를 반복할 때 막는
                    // 안전장치"라는 주석이 실패 반복 상황에서는 실제로 동작하지 않는다는 뜻이다.
                    // 실패 시도까지 세는 별도 카운터가 추가돼야 이름값대로 동작한다.
                    //
                    // 게다가 이 게이트는 RecommendationService보다 우회 경로가 하나 더 많다:
                    // 위 87행의 "deserialized.getTimeline() != null && !isEmpty()" 조건에 걸리면
                    // (캐시가 애초에 빈 타임라인이거나, 111행 catch로 파싱 자체가 실패하면) 이
                    // if 블록 전체를 건너뛰어 게이트를 거치지 않고 곧장 Gemini 재호출로
                    // 흘러간다 — RecommendationService의 cachedResponse==null 우회와 같은
                    // 형태의 갭이 여기선 두 곳(빈 타임라인 + 파싱 실패)에 있다.
                    if (cached.getLastUpdatedDate() != null && today.equals(cached.getLastUpdatedDate())
                            && cached.getDailyUpdateCount() != null && cached.getDailyUpdateCount() >= 3) {
                        // 한도에 막혀 옛 로드맵을 주는 것임을 FE가 안내할 수 있게 플래그를
                        // 붙인다. toBuilder 결과는 반환 전용 — 캐시에 저장하지 않는다
                        // (RecommendationResponse.dailyLimitReached 주석 참고).
                        return deserialized.toBuilder().dailyLimitReached(true).build();
                    }
                }
            } catch (Exception e) {
                log.warn("로드맵 캐시 파싱 실패: {}", e.getMessage());
            }
        }

        String userSpecJson = promptDataBuilder.serializeSpecForRoadmap(userSpec);
        String targetJobStr = promptDataBuilder.buildTargetJobString(targetJob);
        Integer grade       = (userSpec != null) ? userSpec.getGrade() : null;

        // 1. 유사 합격자 케이스 조회 (F-03과 맥락 통일)
        String jobType = (targetJob != null) ? targetJob.getJobType() : null;
        List<PasserData> similarPassers = similarSpecFinder.find(
                jobType,
                (userSpec != null) ? userSpec.getGpa() : null,
                (userSpec != null) ? userSpec.getGpaMax() : null
        );
        String similarCasesStr = promptDataBuilder.buildSimilarCasesText(similarPassers);

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
        try {
            Recommendation cachedRec = recommendationRepository.findByUser_Id(user.getId()).orElse(null);
            if (cachedRec != null && cachedRec.getResultJson() != null) {
                RecommendationResponse recResponse = objectMapper.readValue(cachedRec.getResultJson(), RecommendationResponse.class);
                if (recResponse != null && recResponse.getActivities() != null) {
                    List<RecommendationResponse.ActivityRecommendation> stillOpen = recResponse.getActivities().stream()
                            .filter(a -> a.getDeadline() == null || !a.getDeadline().isBefore(today))
                            .toList();
                    topRecommendedJson = objectMapper.writeValueAsString(stillOpen);
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
        String availableActivitiesJson = promptDataBuilder.buildAvailableActivitiesJson(activeActivities);

        // 4. Gemini API 호출 (최대 2회 시도)
        RoadmapResponse response = callGeminiWithRetry(userSpecJson, targetJobStr, grade,
                similarCasesStr, topRecommendedJson, availableActivitiesJson, activeActivities, today);

        if (response.isAiRoadmap()) {
            roadmapCacheService.save(user, response);
        }
        
        return response;
    }

    private RoadmapResponse callGeminiWithRetry(String userSpecJson, String targetJobStr, Integer grade,
                                                 String similarCasesStr, String topRecommendedJson,
                                                 String availableActivitiesJson, List<Activity> activeActivities,
                                                 LocalDate today) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String rawJson = geminiService.generateRoadmap(
                        userSpecJson, targetJobStr, grade,
                        similarCasesStr, topRecommendedJson, availableActivitiesJson, today);
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

    /**
     * Gemini 미사용/실패 시 DB 등록 활동 기반 기본 로드맵 반환.
     *
     * ⚠️ 예전엔 이 세 시기 라벨이 "N학년 2학기 (9~11월)"부터 시작하도록 하드코딩돼 있었다 —
     * 요청 시점이 실제로 몇 월인지는 전혀 보지 않았다. 4월에 3학년이 폴백을 받으면 1번째
     * (HIGH="지금 집중") 시기로 ~5개월 뒤인 "3학년 2학기"를, 3번째 시기로 ~11개월 뒤인
     * "4학년 1학기"를 보여줘 클래스 상단 문서가 약속하는 "6개월 로드맵"을 벗어났다. grade가
     * null인 분기는 이미 상대적("1~2개월 차"~"5~6개월 차")이라 문제가 없었는데, grade가 있는
     * 분기만 절대 달력이었다. today를 기준으로 현재 속한 학기/방학 구간부터 3개를 순서대로
     * 계산하도록 고친다.
     */
    private RoadmapResponse buildFallbackRoadmap(Integer grade, List<Activity> activeActivities, LocalDate today) {
        String[] periods = computeFallbackPeriods(grade, today);
        String semester1 = periods[0];
        String semester2 = periods[1];
        String semester3 = periods[2];

        // ⚠️ 예전엔 활동을 리스트 인덱스로만 3등분해(0~2→1번째, 3~5→2번째, 6~8→3번째)
        // step1/2/3에 나눠 담았다. activeActivities는 findRecommendableActivities가
        // deadline ASC로 정렬해 준 목록이라, 그때는 "가장 빨리 마감되는 활동들이 가장 먼
        // 미래 시기"에 배정되는 게 항상 가능했다 — computeFallbackPeriods가 세 시기를
        // 여전히 절대 달력(9~11월 고정)으로 보여줄 때는 두 값이 아무 관계도 없어 눈에 안
        // 띄었을 뿐이다. 이제 시기 라벨이 today 기준 실제 달력 구간이 되면서, 예를 들어
        // 8월에 마감하는 활동이 "3학년 겨울방학 (12~2월)" 밑에 나오는 것처럼 카드에 적힌
        // matchedActivities의 마감일과 그 시기 라벨이 정면으로 모순될 수 있다. 시기별
        // 실제 마감일 구간을 계산해 재배정하는 대신(이번 마감 전 범위를 넘는 작업), 첫
        // 번째(HIGH="지금 집중") 시기에만 실제 DB 활동을 붙이고 2·3번째는 텍스트 가이드만
        // 보여준다 — 이러면 최소한 "틀린 날짜 주장"은 절대 나오지 않는다.
        List<MatchedActivity> step1Matched = new ArrayList<>();
        if (activeActivities != null) {
            for (int i = 0; i < activeActivities.size() && i < 3; i++) {
                step1Matched.add(toMatchedActivity(activeActivities.get(i)));
            }
        }
        List<MatchedActivity> step2Matched = List.of();
        List<MatchedActivity> step3Matched = List.of();

        return RoadmapResponse.builder()
                .timeline(List.of(
                        TimelineStep.builder()
                                .period(semester1)
                                .priority("HIGH")
                                .activity("[AI 응답 지연] 핵심 SW 교육 및 인턴십 지원")
                                .reason("[서버 지연 임시 로드맵] 서류 가점 및 기초 실무 역량을 다지는 핵심 시기입니다.")
                                .matchedActivities(step1Matched)
                                .build(),
                        TimelineStep.builder()
                                .period(semester2)
                                .priority("MEDIUM")
                                .activity("[AI 응답 지연] 부트캠프 및 프로젝트 몰입")
                                // ⚠️ "방학 기간을 활용하여"처럼 계절을 못박은 문구는 쓰지 않는다 —
                                // computeFallbackPeriods가 today 기준으로 학기/방학을 동적으로
                                // 계산하면서 semester2가 방학이 아니라 학기(2학기·1학기)인 경우도
                                // 생겼다(예: 7~8월 요청 → 2번째 시기가 "2학기"). 방학이라고 단정하는
                                // 문구가 남아 있으면 그 경우 카드 본문과 기간 라벨이 서로 모순된다.
                                .reason("[서버 지연 임시 로드맵] 이 시기를 활용하여 포트폴리오를 대폭 강화합니다.")
                                .matchedActivities(step2Matched)
                                .build(),
                        TimelineStep.builder()
                                .period(semester3)
                                .priority("LOW")
                                .activity("[AI 응답 지연] 오픈소스 기여 및 해커톤 공모전 참가")
                                .reason("[서버 지연 임시 로드맵] 실무 협업 역량을 입증하고 채용 우대 혜택을 획득합니다.")
                                .matchedActivities(step3Matched)
                                .build()
                ))
                .aiRoadmap(false)
                .build();
    }

    /**
     * grade가 있을 때, today가 속한 학기/방학 구간부터 순서대로 3개의 시기 라벨을 만든다.
     * 한 해를 1학기(3~6월)·여름방학(7~8월)·2학기(9~11월)·겨울방학(12~2월) 4구간으로 보고,
     * 겨울방학 다음엔 학년이 하나 올라간다. 4학년을 넘어가면 "졸업 후 취업 준비"로 고정한다.
     * grade가 null이면 학기 개념이 없으므로 상대적인 "N~N개월 차" 라벨을 그대로 쓴다.
     */
    private String[] computeFallbackPeriods(Integer grade, LocalDate today) {
        if (grade == null) {
            return new String[]{"1~2개월 차", "3~4개월 차", "5~6개월 차"};
        }
        int month = today.getMonthValue();
        int termIndex; // 0=1학기, 1=여름방학, 2=2학기, 3=겨울방학
        if (month >= 3 && month <= 6) termIndex = 0;
        else if (month >= 7 && month <= 8) termIndex = 1;
        else if (month >= 9 && month <= 11) termIndex = 2;
        else termIndex = 3;

        String[] periods = new String[3];
        int curGrade = grade;
        int curTerm = termIndex;
        for (int i = 0; i < 3; i++) {
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
