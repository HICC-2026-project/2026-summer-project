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
    }

    public static GithubProfileResponse notConnected() {
        return GithubProfileResponse.builder().connected(false).build();
    }

    /** targetJobCode는 사용자의 TargetJob.jobType(정규 코드)이다. 목표 미설정이면 null로 넘긴다. */
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

    private static Double findRatio(List<JobRatioItem> ratios, String jobTypeCode) {
        if (ratios == null) return null;
        return ratios.stream()
                .filter(r -> jobTypeCode.equalsIgnoreCase(r.getJobType()))
                .map(JobRatioItem::getRatio)
                .findFirst()
                .orElse(null);
    }

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
                            .build();
                })
                .toList();
    }

    /** "OTHER"는 JobType에 없는 값이라 JobType.labelOf가 원문을 그대로 돌려준다 — 여기서만 "기타"로 고정한다. */
    private static String labelOf(String code) {
        if (code == null) return null;
        if ("OTHER".equalsIgnoreCase(code)) return "기타";
        return JobType.labelOf(code);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer asInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        return Optional.of(value.toString()).map(Integer::parseInt).orElse(null);
    }

    private static Double asDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        return Optional.of(value.toString()).map(Double::parseDouble).orElse(null);
    }
}
