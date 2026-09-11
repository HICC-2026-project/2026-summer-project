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
import java.util.regex.Pattern;

/**
 * 갭(합격자 다수 보유·사용자 미보유 항목)과 활동을 잇는 규칙. Gemini에 의존하지 않는 결정적 로직이다.
 *
 * 두 곳에서 쓴다:
 *  1) Gemini가 돌려준 targetGap 검증 — 알려진 갭(자격증 갭 + 미입력/하위 축)에 없으면 버린다.
 *     Gemini가 "커뮤니케이션 역량" 같은 비교 탭에 없는 갭을 지어내면 두 화면이 다른 말을 하게 된다.
 *  2) Gemini 실패 시 폴백 추천 — 예전엔 활동 목록 앞 3개를 그대로 잘랐다(정렬 기준: 마감 임박순).
 *     목표 직무 태그·갭 키워드로 점수를 매겨 "왜 이 활동인지"가 성립하는 순서로 고른다.
 *
 * 갭은 {@link Gap}(이름 + 매칭 키워드)으로 다룬다. 축 갭은 AxisPosition.axis 코드("LANGUAGE" 등)로
 * 식별하고 화면 라벨은 그 축의 label을 그대로 쓴다 — 라벨 문구를 고쳐도 매칭이 깨지지 않는다.
 */
public final class GapMatcher {

    /** 축 코드(SpecPositionCalculator.buildAxes의 axis 값) → 그 축을 메우는 활동 키워드. */
    private static final Map<String, List<String>> AXIS_KEYWORDS = Map.of(
            "LANGUAGE", List.of("토익", "toeic", "토플", "toefl", "오픽", "opic", "영어", "어학", "텝스", "teps"),
            "EXPERIENCE", List.of("인턴", "intern", "프로젝트", "부트캠프", "bootcamp", "공모전", "해커톤", "hackathon", "경험")
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

    /** 2글자 이하 영문 약어(ai·ml·ui·pm)는 단어 경계가 필요하다 — "html"의 "ml", "email"의 "ai"처럼
     *  더 긴 영문 단어 안에 부분문자열로 걸리는 오매칭을 막는다. 한글·3글자 이상 영문은 부분문자열 유지. */
    public static boolean needsWordBoundary(String keyword) {
        return keyword != null && keyword.length() <= 2
                && keyword.chars().allMatch(c -> c < 128 && Character.isLetterOrDigit(c));
    }

    /** corpus 안에 keyword가 있는지. 짧은 영문 약어는 단어 경계(\b)로 감싸 부분문자열 오매칭을 막는다. */
    private static boolean corpusMatches(String corpus, String keyword) {
        if (needsWordBoundary(keyword)) {
            return Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b").matcher(corpus).find();
        }
        return corpus.contains(keyword);
    }

    /** 직무 코드에 대응하는 활동 태그 키워드(소문자). 활동 검색의 jobType 필터도 같은 표를 쓴다. */
    public static List<String> jobKeywords(JobType job) {
        return JOB_KEYWORDS.getOrDefault(job, List.of());
    }

    /** 알려진 갭 하나. name은 비교 탭에 보이는 이름 그대로, keywords는 활동 본문(소문자)에서 찾을 단어. */
    public record Gap(String name, List<String> keywords) {
    }

    /**
     * 이 비교 결과에서 "갭"으로 부를 수 있는 것 전체. 자격증 갭 + percentile 50 미만이거나 미입력인
     * 어학·경험 축. 순서가 곧 우선순위(자격증 갭은 보유율 내림차순으로 이미 정렬돼 온다).
     */
    public static List<Gap> knownGaps(SpecPositionResult position) {
        List<Gap> gaps = new ArrayList<>();
        if (position == null) {
            return gaps;
        }
        if (position.getGaps() != null) {
            for (SpecPositionResult.SpecGap gap : position.getGaps()) {
                if (gap.getName() != null && !gap.getName().isBlank()) {
                    gaps.add(new Gap(gap.getName(), certKeywords(gap.getName())));
                }
            }
        }
        if (position.getAxes() != null) {
            for (SpecPositionResult.AxisPosition axis : position.getAxes()) {
                List<String> keywords = AXIS_KEYWORDS.get(axis.getAxis());
                boolean weak = axis.getPercentile() == null || axis.getPercentile() < 50;
                if (keywords != null && weak) {
                    gaps.add(new Gap(axis.getLabel(), keywords));
                }
            }
        }
        return gaps;
    }

    /** 프롬프트·로그용 이름 목록. */
    public static List<String> names(List<Gap> gaps) {
        return gaps.stream().map(Gap::name).toList();
    }

    /** Gemini가 준 targetGap을 알려진 갭 이름으로 정규화한다. 못 찾으면 empty. */
    public static Optional<String> normalizeTargetGap(String candidate, List<Gap> knownGaps) {
        if (candidate == null || candidate.isBlank() || knownGaps == null) {
            return Optional.empty();
        }
        String trimmed = candidate.trim();
        String canonical = SpecNormalizer.canonicalCert(trimmed);
        for (Gap gap : knownGaps) {
            if (gap.name().equalsIgnoreCase(trimmed)
                    || (!canonical.isBlank() && SpecNormalizer.canonicalCert(gap.name()).equals(canonical))) {
                return Optional.of(gap.name());
            }
        }
        return Optional.empty();
    }

    /** 활동 본문(이름·설명·태그)에 갭 키워드가 있으면 그 갭. 여러 개면 knownGaps 순서상 앞의 것. */
    public static Optional<String> matchGap(Activity activity, List<Gap> knownGaps) {
        if (activity == null || knownGaps == null || knownGaps.isEmpty()) {
            return Optional.empty();
        }
        return matchGap(corpus(activity), knownGaps);
    }

    private static Optional<String> matchGap(String corpus, List<Gap> knownGaps) {
        for (Gap gap : knownGaps) {
            for (String keyword : gap.keywords()) {
                if (corpusMatches(corpus, keyword)) {
                    return Optional.of(gap.name());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 폴백 추천 순서: 직무 일치(+2) + 갭 매칭(+3) → 같은 점수면 마감 임박순. limit개.
     * 갭 가중을 직무보다 높게 두는 이유: 추천 탭의 존재 이유가 "갭을 메우는 다음 행동"이고,
     * 직무 태그는 시드 데이터에서 여러 직무에 걸쳐 찍히는 경우가 많아 변별력이 낮다.
     */
    public static List<Ranked> rankForFallback(List<Activity> activities, String jobTypeCode,
                                               List<Gap> knownGaps, int limit) {
        List<String> jobKeywords = JobType.from(jobTypeCode).map(JOB_KEYWORDS::get).orElse(List.of());
        List<Ranked> ranked = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Activity a : activities) {
            if (a == null || a.getId() == null || !seen.add(a.getId().toString())) {
                continue;
            }
            String corpus = corpus(a);
            Optional<String> gap = matchGap(corpus, knownGaps);
            boolean jobMatch = jobKeywords.stream().anyMatch(kw -> corpusMatches(corpus, kw));
            int score = (jobMatch ? 2 : 0) + (gap.isPresent() ? 3 : 0);
            ranked.add(new Ranked(a, score, gap.orElse(null)));
        }
        ranked.sort(Comparator
                .comparingInt(Ranked::score).reversed()
                .thenComparing(r -> r.activity().getDeadline(), Comparator.nullsLast(Comparator.naturalOrder())));
        return ranked.size() > limit ? new ArrayList<>(ranked.subList(0, limit)) : ranked;
    }

    /** 자격증 갭 키워드: 원문과 정규화형 — "정보처리기사"와 "정보 처리 기사" 같은 표기 차이를 canonicalCert가 흡수한다. */
    private static List<String> certKeywords(String name) {
        List<String> keys = new ArrayList<>(2);
        keys.add(name.toLowerCase(Locale.ROOT));
        String canonical = SpecNormalizer.canonicalCert(name).toLowerCase(Locale.ROOT);
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
