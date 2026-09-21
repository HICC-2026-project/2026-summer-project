package com.career.recommendation.service;

import com.career.recommendation.entity.PasserData;
import com.career.recommendation.repository.PasserDataRepository;
import com.career.recommendation.util.JobSignalClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * E11-5 — 합격자 제보(PasserReportService)에서 GitHub 아이디 + 동의를 함께 받았을 때, 접수
 * 직후 비동기로 공개 레포를 분석해 PasserData.areas/stack/githubDerived에 파생값만 채운다.
 *
 * GithubAnalysisRunner(E3, 사용자 본인 동의 플로우)와 같은 이유로 PasserReportService와 별도
 * 빈으로 분리한다 — 같은 빈 안에서 this.analyze(...)로 자기 자신을 호출하면 Spring 프록시가
 * @Async 어드바이스를 가로채지 못해 동기적으로 실행돼버린다.
 *
 * ⚠️ username은 이 메서드의 인자로만 존재하고 어디에도 저장·로그되지 않는다. GithubClient가
 * 던지는 예외(GithubUserNotFoundException 등)의 메시지에는 username이 그대로 들어있으므로,
 * 실패를 로그로 남길 때도 e.getMessage()나 e(Throwable) 자체를 로그 인자로 절대 넘기지 않고
 * 예외 클래스명만 남긴다(GithubClient.logSkip과 같은 마스킹 원칙).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasserGithubContributionRunner {

    private final GithubClient githubClient;
    private final PasserDataRepository passerDataRepository;
    private final PlatformTransactionManager transactionManager;

    /** 사용자 개인당 상위 5개(GithubAnalysisRunner의 파생 경험 상한)와 달리, 집계 태그라 조금 더 넉넉히 둔다. */
    private static final int MAX_AGGREGATE_AREAS = 10;
    private static final int MAX_AGGREGATE_STACK = 10;

    /**
     * @param passerDataId 분석 결과를 반영할 PasserData 행
     * @param username     분석 대상 GitHub 아이디 — 로그·DB 어디에도 남기지 않는다
     * @param acceptedYear 합격 연도. null이 아니면 그 해 12/31(포함)까지의 커밋만 집계한다
     */
    @Async("githubTaskExecutor")
    public void analyze(UUID passerDataId, String username, Integer acceptedYear) {
        GithubClient.GithubAnalysisRawResult raw;
        try {
            LocalDate cutoffInclusive = acceptedYear != null ? LocalDate.of(acceptedYear, 12, 31) : null;
            raw = githubClient.analyze(username, cutoffInclusive);
        } catch (Exception e) {
            // 분석이 실패해도 제보 자체는 유효하게 남긴다(조용히 미채움) — 존재하지 않는 계정
            // 등 GithubClient 예외의 메시지엔 아이디가 그대로 담기므로 클래스명만 남긴다.
            log.warn("합격자 제보 GitHub 분석 실패 (reportId={}): {}", passerDataId, e.getClass().getSimpleName());
            return;
        }

        if (raw.repos().isEmpty()) {
            // 컷 이전 공개 레포 기여가 없거나 레이트리밋으로 아무것도 못 모은 경우 — 조용히 미채움.
            return;
        }

        List<JobSignalClassifier.RepoSignal> signals = raw.repos().stream()
                .map(GithubClient.RepoRawData::toSignal)
                .toList();
        JobSignalClassifier.ClassificationResult classification = JobSignalClassifier.classify(signals);

        List<String> areas = aggregateTopTokens(
                classification.repos().stream().map(JobSignalClassifier.RepoClassification::areas).toList(),
                MAX_AGGREGATE_AREAS);
        List<String> stack = aggregateTopTokens(
                classification.repos().stream().map(JobSignalClassifier.RepoClassification::stack).toList(),
                MAX_AGGREGATE_STACK);

        int commitTotal = raw.repos().stream().mapToInt(GithubClient.RepoRawData::commitCount).sum();
        int activeMonths = computeOverallActiveMonths(raw.repos());

        // 집계값만 — 레포 개수 등 숫자와 영역/스택 태그, 직무 비율뿐이다. 레포명·URL은 담지 않는다.
        Map<String, Object> githubDerived = new LinkedHashMap<>();
        githubDerived.put("repos", raw.repos().size());
        githubDerived.put("commits", commitTotal);
        githubDerived.put("activeMonths", activeMonths);
        githubDerived.put("jobRatios", classification.userRatios());

        save(passerDataId, areas, stack, githubDerived);
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

    /** 레포별 토큰(areas 또는 stack)을 등장 레포 수 내림차순으로 합쳐 최대 {@code max}개로 자른다. */
    private static List<String> aggregateTopTokens(List<List<String>> perRepoTokens, int max) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (List<String> tokens : perRepoTokens) {
            if (tokens == null) continue;
            for (String token : tokens) {
                counts.merge(token, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(max)
                .toList();
    }

    private void save(UUID passerDataId, List<String> areas, List<String> stack, Map<String, Object> githubDerived) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status ->
                passerDataRepository.findById(passerDataId).ifPresent(p -> {
                    p.setAreas(areas.toArray(new String[0]));
                    p.setStack(stack.toArray(new String[0]));
                    p.setGithubDerived(githubDerived);
                    passerDataRepository.saveAndFlush(p);
                }));
    }
}
