package com.career.recommendation.service;

import com.career.recommendation.domain.GithubProfileStatus;
import com.career.recommendation.domain.JobType;
import com.career.recommendation.entity.GithubProfile;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.exception.GithubOrganizationAccountException;
import com.career.recommendation.exception.GithubUserNotFoundException;
import com.career.recommendation.repository.GithubProfileRepository;
import com.career.recommendation.repository.UserSpecRepository;
import com.career.recommendation.util.JobSignalClassifier;
import com.career.recommendation.util.ServiceTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * GitHub 분석 실제 실행부(@Async). GithubAnalysisService에서 분리한 이유는 클래스 상단 주석 참고.
 *
 * 이 클래스의 공개 메서드(analyze)는 트랜잭션이 없다 — GithubClient 호출(느린 외부 API, 레포당
 * 최대 4회 순차 호출)을 트랜잭션 밖에서 수행해 DB 커넥션을 점유하지 않는다. DB에 쓰는 지점마다
 * TransactionTemplate으로 짧은 REQUIRES_NEW 트랜잭션을 직접 열고 닫는다 — UserSpecService·
 * TargetJobService와 같은 이유(self-invocation 프록시 우회 문제 없이 REQUIRES_NEW를 보장).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GithubAnalysisRunner {

    private final GithubClient githubClient;
    private final GithubProfileRepository githubProfileRepository;
    private final UserSpecRepository userSpecRepository;
    private final PlatformTransactionManager transactionManager;

    private static final int MAX_DERIVED_EXPERIENCES = 5;
    private static final int MAX_TOTAL_EXPERIENCES = 20;

    @Async("githubTaskExecutor")
    public void analyze(UUID profileId, UUID userId) {
        try {
            GithubProfile profile = githubProfileRepository.findById(profileId).orElse(null);
            if (profile == null) {
                log.warn("GitHub 분석 대상 프로필을 찾을 수 없습니다(이미 삭제됨?): profileId={}", profileId);
                return;
            }
            String username = profile.getUsername();

            GithubClient.GithubAnalysisRawResult raw;
            try {
                raw = githubClient.analyze(username);
            } catch (GithubUserNotFoundException e) {
                saveFailed(profileId, "존재하지 않는 계정입니다.");
                return;
            } catch (GithubOrganizationAccountException e) {
                saveFailed(profileId, "조직 계정은 분석할 수 없습니다.");
                return;
            } catch (Exception e) {
                log.error("GitHub 분석 중 오류 (username={})", username, e);
                saveFailed(profileId, "일시적인 오류로 분석에 실패했습니다.");
                return;
            }

            List<GithubClient.RepoRawData> rawRepos = raw.repos();
            List<JobSignalClassifier.RepoSignal> signals = rawRepos.stream()
                    .map(GithubClient.RepoRawData::toSignal)
                    .toList();
            JobSignalClassifier.ClassificationResult classification = JobSignalClassifier.classify(signals);

            int commitTotal = rawRepos.stream().mapToInt(GithubClient.RepoRawData::commitCount).sum();
            int activeMonths = computeOverallActiveMonths(rawRepos);

            List<Map<String, Object>> repoJson = buildRepoJson(classification.repos(), rawRepos);
            List<Map<String, Object>> ratioJson = buildRatioJson(classification.userRatios());

            String status = raw.rateLimited() ? GithubProfileStatus.RATE_LIMITED.name() : GithubProfileStatus.DONE.name();
            String note = raw.rateLimited() ? "GitHub API 요청 한도로 일부 레포만 분석되었습니다." : null;
            saveSuccess(profileId, status, ratioJson, repoJson, commitTotal, activeMonths, note);

            // 경험 파생은 분석이 "완료(DONE)"됐을 때만 반영한다 — RATE_LIMITED(부분 결과)·FAILED는
            // 기존에 반영돼 있던 GITHUB 파생 경험을 그대로 둔다.
            if (!raw.rateLimited()) {
                mergeDerivedExperiences(userId, classification.repos());
            }
        } catch (Exception e) {
            log.error("GitHub 분석 처리 실패 (profileId={})", profileId, e);
            saveFailed(profileId, "일시적인 오류로 분석에 실패했습니다.");
        }
    }

    private static int computeOverallActiveMonths(List<GithubClient.RepoRawData> repos) {
        LocalDate min = null;
        LocalDate max = null;
        int totalCommits = 0;
        for (GithubClient.RepoRawData repo : repos) {
            totalCommits += repo.commitCount();
            if (repo.firstCommitAt() != null && (min == null || repo.firstCommitAt().isBefore(min))) {
                min = repo.firstCommitAt();
            }
            if (repo.lastCommitAt() != null && (max == null || repo.lastCommitAt().isAfter(max))) {
                max = repo.lastCommitAt();
            }
        }
        return GithubClient.computeActiveMonths(min, max, totalCommits);
    }

    private static List<Map<String, Object>> buildRepoJson(
            List<JobSignalClassifier.RepoClassification> classifications,
            List<GithubClient.RepoRawData> rawRepos) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < classifications.size(); i++) {
            JobSignalClassifier.RepoClassification rc = classifications.get(i);
            GithubClient.RepoRawData raw = rawRepos.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", rc.repoName());
            m.put("primaryJob", rc.primaryJob() != null ? rc.primaryJob().name() : "OTHER");
            m.put("commits", rc.commitCount());
            m.put("files", rc.fileCount());
            m.put("firstCommitAt", raw.firstCommitAt() != null ? raw.firstCommitAt().toString() : null);
            m.put("lastCommitAt", raw.lastCommitAt() != null ? raw.lastCommitAt().toString() : null);
            m.put("mainLanguage", rc.mainLanguage());
            m.put("areas", rc.areas() != null ? rc.areas() : List.of());
            result.add(m);
        }
        result.sort(Comparator.comparingInt((Map<String, Object> m) -> (int) m.get("commits")).reversed());
        return result;
    }

    private static List<Map<String, Object>> buildRatioJson(Map<String, Double> ratios) {
        return ratios.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("jobType", e.getKey());
                    m.put("ratio", Math.round(e.getValue() * 10000.0) / 10000.0);
                    return m;
                })
                .toList();
    }

    private void saveFailed(UUID profileId, String reason) {
        updateProfile(profileId, p -> {
            p.setStatus(GithubProfileStatus.FAILED.name());
            p.setFailureReason(reason);
        });
    }

    private void saveSuccess(UUID profileId, String status, List<Map<String, Object>> ratios,
                              List<Map<String, Object>> repos, int commitTotal, int activeMonths, String note) {
        updateProfile(profileId, p -> {
            p.setStatus(status);
            p.setFailureReason(note);
            p.setJobRatios(ratios);
            p.setRepos(repos);
            p.setCommitTotal(commitTotal);
            p.setActiveMonths(activeMonths);
            p.setAnalyzedAt(LocalDateTime.now(ServiceTime.ZONE_ID));
        });
    }

    private void updateProfile(UUID profileId, Consumer<GithubProfile> mutator) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status ->
                githubProfileRepository.findById(profileId).ifPresent(p -> {
                    mutator.accept(p);
                    githubProfileRepository.saveAndFlush(p);
                }));
    }

    /**
     * source=GITHUB 항목을 전부 제거하고, 커밋 수 상위 레포 최대 5개를 다시 채운다.
     * 전체 20개 상한은 수동 항목을 우선하고 남는 슬롯만 GITHUB 항목에 배정한다.
     */
    private void mergeDerivedExperiences(UUID userId, List<JobSignalClassifier.RepoClassification> repos) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
            UserSpec spec = userSpecRepository.findByUser_Id(userId).orElse(null);
            if (spec == null) {
                // 스펙을 아직 등록하지 않은 사용자 — 파생 경험을 붙일 곳이 없다. 조용히 스킵.
                return;
            }

            List<Map<String, Object>> existing = spec.getExperiences();
            List<Map<String, Object>> manualKept = new ArrayList<>();
            if (existing != null) {
                for (Map<String, Object> exp : existing) {
                    if (!GithubAnalysisService.isGithubSourced(exp)) {
                        manualKept.add(exp);
                    }
                }
            }

            int remainingSlots = MAX_TOTAL_EXPERIENCES - manualKept.size();
            List<Map<String, Object>> derived = new ArrayList<>();
            if (remainingSlots > 0 && !repos.isEmpty()) {
                int take = Math.min(MAX_DERIVED_EXPERIENCES, remainingSlots);
                List<JobSignalClassifier.RepoClassification> top = repos.stream()
                        .sorted(Comparator.comparingInt(JobSignalClassifier.RepoClassification::commitCount).reversed())
                        .limit(take)
                        .toList();
                for (JobSignalClassifier.RepoClassification repo : top) {
                    derived.add(toDerivedExperience(repo));
                }
            }

            List<Map<String, Object>> merged = new ArrayList<>(manualKept);
            merged.addAll(derived);
            if (merged.size() > MAX_TOTAL_EXPERIENCES) {
                merged = merged.subList(0, MAX_TOTAL_EXPERIENCES);
            }

            spec.setExperiences(merged);
            userSpecRepository.saveAndFlush(spec); // updatedAt 갱신 → 추천 캐시 자연 재생성
        });
    }

    private static Map<String, Object> toDerivedExperience(JobSignalClassifier.RepoClassification repo) {
        String jobLabel = repo.primaryJob() != null ? JobType.labelOf(repo.primaryJob().name()) : "기타";
        Map<String, Object> exp = new LinkedHashMap<>();
        exp.put("type", "PROJECT");
        exp.put("title", truncate(repo.repoName(), 100));
        exp.put("description", truncate(
                String.format("GitHub 공개 레포 분석 — 주 직무 %s, 커밋 %d개", jobLabel, repo.commitCount()), 500));
        exp.put("source", "GITHUB");

        // E11(1단계) — 신규 필드는 값이 있을 때만 채운다. months는 ExperienceRequest 계약(1~120)에
        // 맞춰 clamp한다 — repo.activeMonths()가 이미 "그 레포의 첫~마지막 author 커밋 사이
        // 개월수"(GithubClient.computeActiveMonths, 레포 단위)라 별도 재계산이 필요 없다.
        Integer months = clampMonths(repo.activeMonths());
        if (months != null) {
            exp.put("months", months);
        }
        if (repo.stack() != null && !repo.stack().isEmpty()) {
            exp.put("stack", repo.stack());
        }
        if (repo.areas() != null && !repo.areas().isEmpty()) {
            exp.put("areas", repo.areas());
        }
        return exp;
    }

    private static Integer clampMonths(int activeMonths) {
        if (activeMonths <= 0) {
            return null;
        }
        return Math.min(activeMonths, 120);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
