package com.career.recommendation.dto.github;

import com.career.recommendation.domain.JobType;
import com.career.recommendation.entity.GithubProfile;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GET /api/v1/users/me/github 응답. FE와 공유된 확정 계약 — 필드명·상태코드 임의 변경 금지.
 * 미등록이면 connected=false만 채운 인스턴스(다른 필드는 전부 null)를 내려준다.
 */
@Getter
@Builder
public class GithubProfileResponse {

    private boolean connected;
    private String username;
    private String status;
    private String failureReason;
    private LocalDateTime analyzedAt;
    private Integer commitTotal;
    private Integer activeMonths;
    private List<JobRatioItem> jobRatios;
    private Double targetJobMatchRatio;
    private List<RepoItem> repos;

    @Getter
    @Builder
    public static class JobRatioItem {
        private String jobType;
        private String label;
        private Double ratio;
    }

    @Getter
    @Builder
    public static class RepoItem {
        private String name;
        private String primaryJob;
        private String primaryJobLabel;
        private Integer commits;
        private String firstCommitAt;
        private String lastCommitAt;
        private String mainLanguage;
        /** E11(1단계) — 레포 카드에 칩으로 보여줄 기여 영역(ExperienceArea 코드, 최대 5개). */
        private List<String> areas;
    }

    /**
     * 함수 이름: notConnected()
     * 기능: GitHub 계정이 연동되지 않은 상태를 나타내는 응답을 만든다(connected=false, 나머지 필드는 null).
     * 파라미터: 없음
     * 반환값: GithubProfileResponse -> 미연동 상태 응답
     */
    public static GithubProfileResponse notConnected() {
        return GithubProfileResponse.builder().connected(false).build();
    }

    /**
     * 함수 이름: from()
     * 기능: GithubProfile 엔티티를 응답 DTO로 변환한다(connected=true). 직무 비율·레포 목록은 Map에서 DTO 항목으로
     *       바꾸고, 목표 직무 코드가 있으면 그 직무의 비율을 targetJobMatchRatio로 채운다.
     *       targetJobCode는 사용자의 TargetJob.jobType(정규 코드)이다. 목표 미설정이면 null로 넘긴다.
     * 파라미터: GithubProfile profile -> 변환할 GitHub 분석 결과 엔티티
     *           String targetJobCode -> 사용자의 목표 직무 정규 코드(미설정이면 null)
     * 반환값: GithubProfileResponse -> 연동된 상태의 응답 DTO
     */
    public static GithubProfileResponse from(GithubProfile profile, String targetJobCode) {
        List<JobRatioItem> ratios = toJobRatioItems(profile.getJobRatios());
        Double targetMatch = targetJobCode == null ? null : findRatio(ratios, targetJobCode);

        return GithubProfileResponse.builder()
                .connected(true)
                .username(profile.getUsername())
                .status(profile.getStatus())
                .failureReason(profile.getFailureReason())
                .analyzedAt(profile.getAnalyzedAt())
                .commitTotal(profile.getCommitTotal())
                .activeMonths(profile.getActiveMonths())
                .jobRatios(ratios)
                .targetJobMatchRatio(targetMatch)
                .repos(toRepoItems(profile.getRepos()))
                .build();
    }

    /**
     * 함수 이름: findRatio()
     * 기능: 직무 비율 목록에서 주어진 직무 코드(대소문자 무시)와 일치하는 첫 항목의 비율을 찾는다.
     * 파라미터: List<JobRatioItem> ratios -> 직무별 비율 목록(null 가능)
     *           String jobTypeCode -> 찾을 직무 정규 코드
     * 반환값: Double -> 일치하는 직무의 비율, 목록이 null이거나 일치 항목이 없으면 null
     */
    private static Double findRatio(List<JobRatioItem> ratios, String jobTypeCode) {
        if (ratios == null) return null;
        return ratios.stream()
                .filter(r -> jobTypeCode.equalsIgnoreCase(r.getJobType()))
                .map(JobRatioItem::getRatio)
                .findFirst()
                .orElse(null);
    }

    /**
     * 함수 이름: toJobRatioItems()
     * 기능: 저장된 직무 비율 Map 목록을 JobRatioItem 목록으로 변환한다. 각 항목에 jobType, 한글 라벨, ratio를 채운다.
     * 파라미터: List<Map<String, Object>> jobRatios -> "jobType", "ratio" 키를 가진 Map 목록(null 가능)
     * 반환값: List<JobRatioItem> -> 변환된 항목 목록, 입력이 null이면 null
     */
    private static List<JobRatioItem> toJobRatioItems(List<Map<String, Object>> jobRatios) {
        if (jobRatios == null) return null;
        return jobRatios.stream()
                .map(m -> JobRatioItem.builder()
                        .jobType(asString(m.get("jobType")))
                        .label(labelOf(asString(m.get("jobType"))))
                        .ratio(asDouble(m.get("ratio")))
                        .build())
                .toList();
    }

    /**
     * 함수 이름: toRepoItems()
     * 기능: 저장된 레포 정보 Map 목록을 RepoItem 목록으로 변환한다. 대표 직무의 한글 라벨과 기여 영역(areas)도 함께 채운다.
     * 파라미터: List<Map<String, Object>> repos -> 레포별 정보 Map 목록(null 가능)
     * 반환값: List<RepoItem> -> 변환된 레포 항목 목록, 입력이 null이면 null
     */
    private static List<RepoItem> toRepoItems(List<Map<String, Object>> repos) {
        if (repos == null) return null;
        return repos.stream()
                .map(m -> {
                    String primaryJob = asString(m.get("primaryJob"));
                    return RepoItem.builder()
                            .name(asString(m.get("name")))
                            .primaryJob(primaryJob)
                            .primaryJobLabel(labelOf(primaryJob))
                            .commits(asInteger(m.get("commits")))
                            .firstCommitAt(asString(m.get("firstCommitAt")))
                            .lastCommitAt(asString(m.get("lastCommitAt")))
                            .mainLanguage(asString(m.get("mainLanguage")))
                            .areas(asStringList(m.get("areas")))
                            .build();
                })
                .toList();
    }

    /**
     * 함수 이름: labelOf()
     * 기능: 직무 코드를 화면에 보여줄 한글 라벨로 바꾼다. "OTHER"는 JobType에 없는 값이라
     *       JobType.labelOf가 원문을 그대로 돌려준다 — 여기서만 "기타"로 고정한다.
     * 파라미터: String code -> 직무 코드(null 가능)
     * 반환값: String -> 한글 라벨, code가 null이면 null, "OTHER"(대소문자 무시)이면 "기타"
     */
    private static String labelOf(String code) {
        if (code == null) return null;
        if ("OTHER".equalsIgnoreCase(code)) return "기타";
        return JobType.labelOf(code);
    }

    /**
     * 함수 이름: asString()
     * 기능: Map에서 꺼낸 임의의 값을 문자열로 바꾼다.
     * 파라미터: Object value -> 변환할 값(null 가능)
     * 반환값: String -> 문자열 표현, value가 null이면 null
     */
    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 함수 이름: asInteger()
     * 기능: Map에서 꺼낸 임의의 값을 정수로 바꾼다. Number면 intValue, 그 외에는 문자열로 보고 파싱한다.
     * 파라미터: Object value -> 변환할 값(null 가능)
     * 반환값: Integer -> 변환된 정수, value가 null이면 null
     * 예외: NumberFormatException -> Number가 아닌 값이 정수 문자열이 아닐 때
     */
    private static Integer asInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        return Optional.of(value.toString()).map(Integer::parseInt).orElse(null);
    }

    /**
     * 함수 이름: asStringList()
     * 기능: Map에서 꺼낸 값이 List이면 각 원소를 문자열로 바꾼 목록으로 변환한다.
     * 파라미터: Object value -> 변환할 값(List가 아니거나 null 가능)
     * 반환값: List<String> -> 문자열 목록, value가 List가 아니면 null
     */
    private static List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        return list.stream().map(String::valueOf).toList();
    }

    /**
     * 함수 이름: asDouble()
     * 기능: Map에서 꺼낸 임의의 값을 실수로 바꾼다. Number면 doubleValue, 그 외에는 문자열로 보고 파싱한다.
     * 파라미터: Object value -> 변환할 값(null 가능)
     * 반환값: Double -> 변환된 실수, value가 null이면 null
     * 예외: NumberFormatException -> Number가 아닌 값이 실수 문자열이 아닐 때
     */
    private static Double asDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        return Optional.of(value.toString()).map(Double::parseDouble).orElse(null);
    }
}
