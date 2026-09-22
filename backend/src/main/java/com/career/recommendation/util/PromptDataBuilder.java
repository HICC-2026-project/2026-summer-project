package com.career.recommendation.util;

import com.career.recommendation.dto.position.SpecPositionResult;
import com.career.recommendation.entity.Activity;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.UserSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * BE-1 담당 — Gemini 프롬프트에 주입할 데이터를 변환하는 공통 유틸리티.
 *
 * RecommendationService, RoadmapService에서 중복되던 메서드를 이곳으로 통합하여
 * 단일 진실 원천(Single Source of Truth)을 보장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptDataBuilder {

    private final ObjectMapper objectMapper;

    /**
     * DB 활성 활동 목록을 Gemini 프롬프트용 JSON 문자열로 변환한다.
     * 각 활동의 id, type, name, organization, description, deadline, tags 정보를 포함한다.
     */
    public String buildAvailableActivitiesJson(List<Activity> activities) {
        return buildActivitiesJson(activities, Set.of(), true);
    }

    /**
     * 로드맵(F-05) 프롬프트용 "전체 DB 등록 활동 목록"을 만든다. buildAvailableActivitiesJson과 달리:
     * - excludeIds에 있는 활동(이미 [우선 반영할 AI 추천 활동]에 포함된 활동)은 제외한다 — 같은 활동이
     *   두 목록에 중복으로 실려 프롬프트 토큰만 낭비하는 것을 막는다.
     * - targetSpec 필드를 포함하지 않는다 — 크롤 수집 활동은 required_qualifications가 수천 자에
     *   달할 수 있는데, 로드맵은 시기별 매칭(마감일·직무)만 할 뿐 targetSpec 충족 여부는 따지지
     *   않는다(그건 추천(F-03) 프롬프트의 역할). description 100자 컷은 그대로 유지한다.
     */
    public String buildAvailableActivitiesJsonForRoadmap(List<Activity> activities, Set<UUID> excludeIds) {
        return buildActivitiesJson(activities, excludeIds != null ? excludeIds : Set.of(), false);
    }

    private String buildActivitiesJson(List<Activity> activities, Set<UUID> excludeIds, boolean includeTargetSpec) {
        if (activities.isEmpty()) return "[]";
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Activity a : activities) {
                if (excludeIds.contains(a.getId())) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", a.getId().toString());
                item.put("type", a.getType());
                item.put("name", a.getName());
                item.put("organization", a.getOrganization());
                if (a.getDescription() != null) {
                    String desc = a.getDescription();
                    item.put("description", desc.length() > 100 ? desc.substring(0, 100) + "..." : desc);
                }
                if (a.getDeadline() != null) {
                    item.put("deadline", a.getDeadline().toString());
                }
                if (a.getTags() != null) {
                    item.put("tags", a.getTags());
                }
                // 활동 자격 요건(targetSpec)을 포함하여 AI가 유저 스펙과 비교할 수 있도록 전달
                // (추천(F-03)에서만 필요 — 로드맵은 includeTargetSpec=false로 호출한다)
                if (includeTargetSpec && a.getTargetSpec() != null) {
                    item.put("targetSpec", a.getTargetSpec());
                }
                list.add(item);
            }
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("활동 목록 JSON 변환 실패: {}", e.getMessage());
            return "[]";
        }
    }

    /** 프롬프트에 실을 경험 description 상한. 저장·API 응답(UserSpecResponse 등)은 이 상한과 무관하다. */
    private static final int EXPERIENCE_DESCRIPTION_PROMPT_LIMIT = 100;
    /** 프롬프트에 실을 경험당 stack 상한 개수. */
    private static final int EXPERIENCE_STACK_PROMPT_LIMIT = 5;

    /**
     * 유저 스펙을 Gemini 프롬프트용 JSON 문자열로 직렬화한다.
     * 추천(F-03)용 — 학점, 학점 만점, 어학, 자격증, 경험, 학년 모두 포함.
     */
    public String serializeSpecForRecommendation(UserSpec userSpec) {
        if (userSpec == null) return "{}";
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "gpa", userSpec.getGpa() != null ? userSpec.getGpa() : "없음",
                    "gpaMax", userSpec.getGpaMax() != null ? userSpec.getGpaMax() : 4.5,
                    "languageScores", userSpec.getLanguageScores() != null ? userSpec.getLanguageScores() : List.of(),
                    "certifications", userSpec.getCertifications() != null ? userSpec.getCertifications() : new String[]{},
                    "experiences", truncateExperiencesForPrompt(userSpec.getExperiences()),
                    "grade", userSpec.getGrade() != null ? userSpec.getGrade() : "미입력"
            ));
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 유저 스펙을 Gemini 프롬프트용 JSON 문자열로 직렬화한다.
     * 로드맵(F-05)용 — 학점, 학년, 자격증, 어학 성적, 경험 포함.
     *
     * ⚠️ 어학 성적(languageScores)을 반드시 포함한다. 예전엔 학점·학년·자격증만 보내서
     * Gemini가 사용자의 어학 보유 여부를 알 수 없었고, 이미 토익 900을 가진 사용자에게
     * "토익 900 목표로 학습" 같은 스텝을 추천하거나 반대로 진짜 어학 공백을 놓치는
     * "이상한 결론"이 로드맵에 나올 수 있었다(추천 프롬프트 쪽 직렬화에는 원래부터
     * 어학이 포함돼 있어 두 프롬프트가 서로 다른 스펙을 보고 있었다).
     */
    public String serializeSpecForRoadmap(UserSpec userSpec) {
        if (userSpec == null) return "{}";
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "gpa", userSpec.getGpa() != null ? userSpec.getGpa() : "없음",
                    "grade", userSpec.getGrade() != null ? userSpec.getGrade() : "미입력",
                    "certifications", userSpec.getCertifications() != null ? userSpec.getCertifications() : new String[]{},
                    "languageScores", userSpec.getLanguageScores() != null ? userSpec.getLanguageScores() : List.of(),
                    "experiences", truncateExperiencesForPrompt(userSpec.getExperiences())
            ));
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 경험 리스트를 프롬프트 주입용으로 축약한다. 저장 형식(UserSpec.experiences)과 API 응답은
     * 그대로 두고, Gemini에 보낼 때만 크기를 줄인다 — 경험 최대 20개 × description 500자 +
     * stack 10개 등을 그대로 넣으면 사용자 1명의 경험만으로 프롬프트가 2만자를 넘을 수 있었다.
     * - description: 100자로 컷(+"...")
     * - stack: 앞 5개만
     * - role/areas/months/type/title/depth 등 그 외 필드는 그대로 둔다(role은 이미 ≤100자,
     *   areas는 코드 나열이라 원래도 짧다).
     * 두 직렬화 메서드(추천·로드맵)가 중복 구현하지 않도록 이곳에 단일화한다.
     */
    private List<Map<String, Object>> truncateExperiencesForPrompt(List<Map<String, Object>> experiences) {
        if (experiences == null || experiences.isEmpty()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>(experiences.size());
        for (Map<String, Object> experience : experiences) {
            if (experience == null) continue;
            Map<String, Object> copy = new LinkedHashMap<>(experience);
            Object description = copy.get("description");
            if (description instanceof String desc && desc.length() > EXPERIENCE_DESCRIPTION_PROMPT_LIMIT) {
                copy.put("description", desc.substring(0, EXPERIENCE_DESCRIPTION_PROMPT_LIMIT) + "...");
            }
            Object stack = copy.get("stack");
            if (stack instanceof List<?> stackList && stackList.size() > EXPERIENCE_STACK_PROMPT_LIMIT) {
                copy.put("stack", new ArrayList<>(stackList.subList(0, EXPERIENCE_STACK_PROMPT_LIMIT)));
            }
            result.add(copy);
        }
        return result;
    }

    /**
     * 위치·갭 계산 결과(SpecPositionResult)를 Gemini 프롬프트용 텍스트로 변환한다.
     *
     * 예전 buildSimilarCasesText(유사 합격자 5명의 원본 스펙 나열)를 대체한다 — 개별 케이스
     * 나열보다 "분포 내 위치 + 부족한 것(갭)"이 추천 이유 생성에 더 직접적인 신호이고,
     * 화면(specPosition)과 프롬프트가 같은 데이터를 보므로 추천 이유와 비교 탭이 서로
     * 모순될 수 없다.
     */
    public String buildPositionContextText(SpecPositionResult position) {
        if (position == null || SpecPositionCalculator.BASIS_NONE.equals(position.getBasis())) {
            return "합격자 비교 데이터 없음";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(position.getBasisMessage()).append('\n');
        if (position.getAxes() != null) {
            for (SpecPositionResult.AxisPosition axis : position.getAxes()) {
                sb.append(String.format("- %s: 내 값 %s / 합격자 중앙값 %s%s\n",
                        axis.getLabel(), axis.getMyValue(), axis.getMedianValue(),
                        axis.getPercentile() != null
                                ? String.format(" (합격자 분포에서 percentile %d)", axis.getPercentile())
                                : ""));
            }
        }
        // E11-2 — 목표 직무 요구 영역 중 보유/미보유를 명시해, 추천이 "미보유 영역을 채우는
        // 활동"으로 좁혀지게 한다. areaCoverage가 null(목표 직무 미설정)이거나 빈 리스트면 생략.
        if (position.getAreaCoverage() != null && !position.getAreaCoverage().isEmpty()) {
            List<String> held = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            for (SpecPositionResult.AreaCoverage coverage : position.getAreaCoverage()) {
                (coverage.isCovered() ? held : missing).add(coverage.getLabel());
            }
            sb.append(String.format("목표 직무 요구 영역 — 보유: %s / 미보유: %s\n",
                    held.isEmpty() ? "없음" : String.join(", ", held),
                    missing.isEmpty() ? "없음" : String.join(", ", missing)));
        }
        if (position.getGaps() != null && !position.getGaps().isEmpty()) {
            sb.append("부족한 항목(갭 — 합격자 다수 보유, 사용자 미보유):\n");
            for (SpecPositionResult.SpecGap gap : position.getGaps()) {
                sb.append(String.format("- %s (합격자 %d%% 보유)\n",
                        gap.getName(), gap.getHolderRatePercent()));
            }
        }
        // targetGap에 쓸 수 있는 이름을 닫힌 목록으로 준다 — 추천 카드와 비교 탭이 같은 갭 이름을 쓰게 하기 위함.
        // 순서가 곧 우선순위(자격증 갭은 보유율 내림차순, 그 뒤 어학·경험 축).
        List<String> gapNames = GapMatcher.names(GapMatcher.knownGaps(position));
        if (!gapNames.isEmpty()) {
            sb.append("targetGap에 쓸 수 있는 갭 이름(우선순위 순): ")
                    .append(String.join(", ", gapNames)).append('\n');
        } else {
            sb.append("targetGap에 쓸 수 있는 갭 이름: 없음\n");
        }
        return sb.toString();
    }

    /**
     * 목표 직무 정보를 프롬프트용 문자열로 변환한다.
     */
    public String buildTargetJobString(TargetJob targetJob) {
        if (targetJob == null) return "미설정";
        // companySize·industry는 선택 입력이라 null일 수 있다(TargetJobRequest에 @NotBlank가
        // 없다). null 폴백 없이 그대로 이어붙이면 문자열 결합 규칙상 "BACKEND / null / null"이
        // 되어 그 리터럴 "null"이 Gemini 프롬프트에 그대로 들어간다 — 다른 필드들처럼
        // "미설정"으로 폴백한다.
        String companySize = targetJob.getCompanySize() != null ? targetJob.getCompanySize() : "미설정";
        String industry = targetJob.getIndustry() != null ? targetJob.getIndustry() : "미설정";
        return targetJob.getJobType() + " / " + companySize + " / " + industry;
    }
}
