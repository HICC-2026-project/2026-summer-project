package com.career.recommendation.util;

import com.career.recommendation.domain.JobType;
import com.career.recommendation.dto.position.SpecPositionResult;
import com.career.recommendation.entity.Activity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 갭(합격자 다수 보유·사용자 미보유 항목)과 활동을 잇는 규칙. Gemini에 의존하지 않는 결정적 로직이다.
 *
 * 두 곳에서 쓴다:
 *  1) Gemini가 돌려준 targetGap 검증 — 알려진 갭 이름(자격증 갭 + 미입력/하위 축 라벨)에 없으면 버린다.
 *     Gemini가 "커뮤니케이션 역량" 같은 비교 탭에 없는 갭을 지어내면 두 화면이 다른 말을 하게 된다.
 *  2) Gemini 실패 시 폴백 추천 — 예전엔 활동 목록 앞 3개를 그대로 잘랐다(정렬 기준: 마감 임박순).
 *     목표 직무 태그·갭 키워드로 점수를 매겨 "왜 이 활동인지"가 성립하는 순서로 고른다.
 */
public final class GapMatcher {

    /** 축 라벨 그대로 갭 이름으로 쓴다 — SpecPositionCalculator.buildAxes의 label과 일치해야 한다. */
    public static final String GAP_LANGUAGE = "어학 성적";
    public static final String GAP_EXPERIENCE = "경험";

    /** 갭 이름에 대응하는 활동 키워드. 자격증 갭은 이름 자체(정규화)를 키워드로 쓴다. */
    private static final Map<String, List<String>> AXIS_KEYWORDS = Map.of(
            GAP_LANGUAGE, List.of("토익", "toeic", "토플", "toefl", "오픽", "opic", "영어", "어학", "텝스", "teps"),
            GAP_EXPERIENCE, List.of("인턴", "intern", "프로젝트", "부트캠프", "bootcamp", "공모전", "해커톤", "hackathon", "경험")
    );

    /** 활동 tags(시드 기준 한글)와 직무 코드를 잇는다. */
    private static final Map<JobType, List<String>> JOB_KEYWORDS = Map.of(
            JobType.BACKEND, List.of("백엔드", "backend", "서버", "server", "클라우드", "cloud"),
            JobType.FRONTEND, List.of("프론트엔드", "frontend", "프론트", "웹", "web", "ui"),
            JobType.DATA_ENGINEER, List.of("데이터", "data"),
            JobType.AI_ML, List.of("ai", "인공지능", "머신러닝", "ml", "딥러닝"),
            JobType.PM, List.of("기획", "pm", "서비스기획", "프로덕트"),
            JobType.SECURITY, List.of("보안", "security", "해킹", "ctf")
    );

    private GapMatcher() {
    }

    /**
     * 이 비교 결과에서 "갭"으로 부를 수 있는 이름 전체. 자격증 갭 + percentile 50 미만이거나
     * 미입력인 어학·경험 축. 순서는 비교 탭과 같다(자격증 갭은 보유율 내림차순으로 이미 정렬돼 온다).
     */
    public static List<String> knownGapNames(SpecPositionResult position) {
        List<String> names = new ArrayList<>();
        if (position == null) {
            return names;
        }
        if (position.getGaps() != null) {
            for (SpecPositionResult.SpecGap gap : position.getGaps()) {
                if (gap.getName() != null && !gap.getName().isBlank()) {
                    names.add(gap.getName());
                }
            }
        }
        if (position.getAxes() != null) {
            for (SpecPositionResult.AxisPosition axis : position.getAxes()) {
                boolean weak = axis.getPercentile() == null || axis.getPercentile() < 50;
                if (weak && (GAP_LANGUAGE.equals(axis.getLabel()) || GAP_EXPERIENCE.equals(axis.getLabel()))) {
                    names.add(axis.getLabel());
                }
            }
        }
        return names;
    }

    /** Gemini가 준 targetGap을 알려진 갭 이름으로 정규화한다. 못 찾으면 empty. */
    public static Optional<String> normalizeTargetGap(String candidate, List<String> knownGaps) {
        if (candidate == null || candidate.isBlank() || knownGaps == null) {
            return Optional.empty();
        }
        String c = SpecNormalizer.canonicalCert(candidate);
        for (String known : knownGaps) {
            if (known.equalsIgnoreCase(candidate.trim())
                    || SpecNormalizer.canonicalCert(known).equals(c)) {
                return Optional.of(known);
            }
        }
        return Optional.empty();
    }

    /** 활동 본문(이름·설명·태그)에 갭 키워드가 있으면 그 갭. 여러 개면 knownGaps 순서상 앞의 것. */
    public static Optional<String> matchGap(Activity activity, List<String> knownGaps) {
        if (activity == null || knownGaps == null || knownGaps.isEmpty()) {
            return Optional.empty();
        }
        String haystack = corpus(activity);
        for (String gap : knownGaps) {
            for (String keyword : keywordsFor(gap)) {
                if (!keyword.isBlank() && haystack.contains(keyword)) {
                    return Optional.of(gap);
                }
            }
        }
        return Optional.empty();
    }

    public static boolean matchesJob(Activity activity, String jobTypeCode) {
        Optional<JobType> job = JobType.from(jobTypeCode);
        if (activity == null || job.isEmpty()) {
            return false;
        }
        String haystack = corpus(activity);
        return JOB_KEYWORDS.getOrDefault(job.get(), List.of()).stream().anyMatch(haystack::contains);
    }

    /**
     * 폴백 추천 순서: 직무 일치(+2) + 갭 매칭(+3) → 같은 점수면 마감 임박순. limit개.
     * 갭 가중을 직무보다 높게 두는 이유: 추천 탭의 존재 이유가 "갭을 메우는 다음 행동"이고,
     * 직무 태그는 시드 데이터에서 여러 직무에 걸쳐 찍히는 경우가 많아 변별력이 낮다.
     */
    public static List<Ranked> rankForFallback(List<Activity> activities, String jobTypeCode,
                                               List<String> knownGaps, int limit) {
        List<Ranked> ranked = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Activity a : activities) {
            if (a == null || a.getId() == null || !seen.add(a.getId().toString())) {
                continue;
            }
            Optional<String> gap = matchGap(a, knownGaps);
            int score = (matchesJob(a, jobTypeCode) ? 2 : 0) + (gap.isPresent() ? 3 : 0);
            ranked.add(new Ranked(a, score, gap.orElse(null)));
        }
        ranked.sort(Comparator
                .comparingInt(Ranked::score).reversed()
                .thenComparing(r -> r.activity().getDeadline(), Comparator.nullsLast(Comparator.naturalOrder())));
        return ranked.size() > limit ? new ArrayList<>(ranked.subList(0, limit)) : ranked;
    }

    private static List<String> keywordsFor(String gap) {
        List<String> axis = AXIS_KEYWORDS.get(gap);
        if (axis != null) {
            return axis;
        }
        // 자격증 갭: 원문과 정규화형 둘 다 — "정보처리기사"와 "정처기" 같은 표기 차이를 canonicalCert가 흡수한다.
        List<String> keys = new ArrayList<>();
        keys.add(gap.toLowerCase(Locale.ROOT));
        String canonical = SpecNormalizer.canonicalCert(gap).toLowerCase(Locale.ROOT);
        if (!canonical.isBlank() && !keys.contains(canonical)) {
            keys.add(canonical);
        }
        return keys;
    }

    private static String corpus(Activity a) {
        StringBuilder sb = new StringBuilder();
        if (a.getName() != null) sb.append(a.getName()).append(' ');
        if (a.getDescription() != null) sb.append(a.getDescription()).append(' ');
        if (a.getTags() != null) {
            for (String tag : a.getTags()) {
                if (tag != null) sb.append(tag).append(' ');
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    public record Ranked(Activity activity, int score, String targetGap) {
    }
}
