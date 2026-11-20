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

    /**
     * 함수 이름: analyze()
     * 기능: 비동기로 프로필의 GitHub 계정을 분석해 직무 비율·레포 목록·커밋 수·활동 개월을 저장하고,
     * 분석이 완료(DONE)된 경우에만 GITHUB 파생 경험을 사용자 스펙에 반영한다. 실패하면 FAILED로 기록한다.
     * 파라미터: UUID profileId -> 분석할 GithubProfile ID
     *           UUID userId -> 파생 경험을 반영할 사용자 ID
     * 반환값: 없음
     */
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

    /**
     * 함수 이름: computeOverallActiveMonths()
     * 기능: 전체 레포의 가장 이른 첫 커밋과 가장 늦은 마지막 커밋 날짜, 총 커밋 수로 전체 활동 개월 수를 계산한다.
     * 파라미터: List<GithubClient.RepoRawData> repos -> 레포별 원시 수집 결과
     * 반환값: int -> 전체 활동 개월 수
     */
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

    /**
     * 함수 이름: buildRepoJson()
     * 기능: 레포 분류 결과와 원시 수집 결과(같은 순서)를 합쳐 프로필에 저장할 레포 JSON 목록을 만들고 커밋 수 내림차순으로 정렬한다.
     * 파라미터: List<JobSignalClassifier.RepoClassification> classifications -> 레포별 직무 분류 결과
     *           List<GithubClient.RepoRawData> rawRepos -> 레포별 원시 수집 결과(classifications와 같은 순서)
     * 반환값: List<Map<String, Object>> -> 레포별 JSON 맵 목록(커밋 수 내림차순)
     */
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

    /**
     * 함수 이름: buildRatioJson()
     * 기능: 직무별 비율 맵을 비율 내림차순의 {jobType, ratio} 목록으로 바꾼다. 비율은 소수점 4자리로 반올림한다.
     * 파라미터: Map<String, Double> ratios -> 직무 코드별 비율
     * 반환값: List<Map<String, Object>> -> 비율 내림차순 JSON 맵 목록
     */
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

    /**
     * 함수 이름: saveFailed()
     * 기능: 프로필 상태를 FAILED로 바꾸고 실패 사유를 기록한다(별도 REQUIRES_NEW 트랜잭션).
     * 파라미터: UUID profileId -> 대상 GithubProfile ID
     *           String reason -> 사용자에게 보여줄 실패 사유
     * 반환값: 없음
     */
    private void saveFailed(UUID profileId, String reason) {
        updateProfile(profileId, p -> {
            p.setStatus(GithubProfileStatus.FAILED.name());
            p.setFailureReason(reason);
        });
    }

    /**
     * 함수 이름: saveSuccess()
     * 기능: 분석 결과(상태·직무 비율·레포 목록·커밋 수·활동 개월·분석 시각)를 프로필에 저장한다(별도 REQUIRES_NEW 트랜잭션).
     * note는 failureReason 필드에 기록되어 부분 분석 안내 등에 쓰인다.
     * 파라미터: UUID profileId -> 대상 GithubProfile ID
     *           String status -> 저장할 상태(DONE 또는 RATE_LIMITED)
     *           List<Map<String, Object>> ratios -> 직무 비율 JSON
     *           List<Map<String, Object>> repos -> 레포 목록 JSON
     *           int commitTotal -> 전체 커밋 수
     *           int activeMonths -> 전체 활동 개월 수
     *           String note -> 안내 문구(없으면 null)
     * 반환값: 없음
     */
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

    /**
     * 함수 이름: updateProfile()
     * 기능: 새 REQUIRES_NEW 트랜잭션에서 프로필을 조회해 mutator로 수정한 뒤 즉시 저장(flush)한다. 프로필이 없으면 아무것도 하지 않는다.
     * 파라미터: UUID profileId -> 대상 GithubProfile ID
     *           Consumer<GithubProfile> mutator -> 프로필 필드를 변경하는 함수
     * 반환값: 없음
     */
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
     * 함수 이름: mergeDerivedExperiences()
     * 기능: source=GITHUB 항목을 전부 제거하고, 커밋 수 상위 레포 최대 5개를 다시 채운다.
     * 전체 20개 상한은 수동 항목을 우선하고 남는 슬롯만 GITHUB 항목에 배정한다.
     * 스펙이 아직 없는 사용자는 조용히 건너뛴다.
     * 파라미터: UUID userId -> 경험을 갱신할 사용자 ID
     *           List<JobSignalClassifier.RepoClassification> repos -> 레포별 분류 결과
     * 반환값: 없음
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

    /**
     * 함수 이름: toDerivedExperience()
     * 기능: 레포 분류 결과를 source=GITHUB인 PROJECT 경험 항목으로 변환한다. 제목·설명은 길이를 제한하고,
     * months·stack·areas는 값이 있을 때만 채운다.
     * 파라미터: JobSignalClassifier.RepoClassification repo -> 레포 분류 결과
     * 반환값: Map<String, Object> -> 경험 항목 맵
     */
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

    /**
     * 함수 이름: clampMonths()
     * 기능: 활동 개월 수를 경험 요청 계약 범위(최대 120)로 제한한다. 0 이하면 값이 없는 것으로 본다.
     * 파라미터: int activeMonths -> 활동 개월 수
     * 반환값: Integer -> 1~120으로 제한된 개월 수(0 이하면 null)
     */
    private static Integer clampMonths(int activeMonths) {
        if (activeMonths <= 0) {
            return null;
        }
        return Math.min(activeMonths, 120);
    }

    /**
     * 함수 이름: truncate()
     * 기능: 문자열이 최대 길이를 넘으면 앞에서부터 잘라낸다.
     * 파라미터: String value -> 원본 문자열(null 허용)
     *           int maxLength -> 최대 길이
     * 반환값: String -> 잘린 문자열(null이면 null)
     */
    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
