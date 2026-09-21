package com.career.recommendation.util;

import com.career.recommendation.dto.position.JobSpecProfile;
import com.career.recommendation.dto.position.JobSpecProfile.CertStat;
import com.career.recommendation.entity.PasserData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BE-1 담당 — 합격자 목록을 JobSpecProfile(직무별 분포·보유율 집계)로 변환한다.
 * DB 조회·캐싱은 JobSpecProfileService가, 여기서는 순수 집계만 담당한다
 * (순수 함수라 단위 테스트가 DB 없이 돈다).
 */
@Component
public class JobSpecProfileBuilder {

    public JobSpecProfile build(String jobType, List<PasserData> passers) {
        List<PasserData> valid = passers == null
                ? List.of()
                : passers.stream().filter(Objects::nonNull).toList();
        int sampleSize = valid.size();

        double[] gpaRatios = valid.stream()
                .filter(p -> p.getGpa() != null && p.getGpaMax() != null && p.getGpaMax().signum() > 0)
                .mapToDouble(p -> p.getGpa().doubleValue() / p.getGpaMax().doubleValue())
                .sorted()
                .toArray();

        double[] toeicEquivalents = valid.stream()
                .mapToDouble(p -> SpecNormalizer.maxEquivalentToeic(p.getLanguageScores()))
                .filter(value -> value > 0)
                .sorted()
                .toArray();

        int[] experienceCounts = valid.stream()
                .filter(p -> p.getExperienceCount() != null)
                .mapToInt(PasserData::getExperienceCount)
                .sorted()
                .toArray();

        // 자격증: 1인당 개수 분포(0개 포함 전원)와 canonical별 보유자 수를 한 번의 순회로 집계.
        // 한 사람이 같은 자격증을 중복 표기해도(정규화 후 같아지면) 1개·1명으로 센다.
        int[] certCounts = new int[sampleSize];
        Map<String, Integer> holderCounts = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> rawFormCounts = new HashMap<>();
        int index = 0;
        for (PasserData passer : valid) {
            Set<String> canonicals = SpecNormalizer.canonicalCerts(passer.getCertifications());
            certCounts[index++] = canonicals.size();
            for (String canonical : canonicals) {
                holderCounts.merge(canonical, 1, Integer::sum);
            }
            if (passer.getCertifications() != null) {
                for (String raw : passer.getCertifications()) {
                    String canonical = SpecNormalizer.canonicalCert(raw);
                    if (canonical.isBlank()) continue;
                    rawFormCounts.computeIfAbsent(canonical, k -> new LinkedHashMap<>())
                            .merge(raw.trim(), 1, Integer::sum);
                }
            }
        }
        Arrays.sort(certCounts);

        List<CertStat> certStats = new ArrayList<>(holderCounts.size());
        holderCounts.forEach((canonical, holders) -> certStats.add(CertStat.builder()
                .canonicalName(canonical)
                .displayName(mostCommonRawForm(rawFormCounts.get(canonical), canonical))
                .holders(holders)
                .holderRate((double) holders / sampleSize)
                .build()));
        // 보유율 내림차순, 동률이면 이름순 — 갭 리스트가 위에서부터 자르므로 순서가 결정적이어야
        // 캐시 재생성 때마다 갭 목록이 뒤바뀌지 않는다.
        certStats.sort(Comparator.comparingDouble(CertStat::getHolderRate).reversed()
                .thenComparing(CertStat::getCanonicalName));

        // DEMO·출처 미상 데이터 포함 여부 — dataOrigin null도 미상으로 본다(기존
        // containsSampleOrUnclassifiedData 판정 계승).
        boolean containsDemoData = valid.stream().anyMatch(p -> p.getDataOrigin() == null
                || PasserData.ORIGIN_DEMO.equalsIgnoreCase(p.getDataOrigin())
                || "UNKNOWN".equalsIgnoreCase(p.getDataOrigin()));

        // E11-5 — github_derived가 있는(GitHub 아이디 제보·분석 성공) 합격자만 영역 보유율의
        // 표본으로 삼는다. areas가 null/빈 배열이어도 github_derived가 있으면 "레포는 분석됐지만
        // 감지된 영역이 없다"는 실측이므로 분모에는 포함하고 보유 영역만 0개로 집계한다.
        List<PasserData> withGithub = valid.stream()
                .filter(p -> p.getGithubDerived() != null && !p.getGithubDerived().isEmpty())
                .toList();
        int githubSampleSize = withGithub.size();
        Map<String, Integer> areaHolderCounts = new LinkedHashMap<>();
        for (PasserData passer : withGithub) {
            Set<String> areasOfPasser = passer.getAreas() == null
                    ? Set.of()
                    : new LinkedHashSet<>(Arrays.asList(passer.getAreas()));
            for (String area : areasOfPasser) {
                areaHolderCounts.merge(area, 1, Integer::sum);
            }
        }
        Map<String, Double> areaRatios = areaHolderCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> (double) e.getValue() / githubSampleSize,
                        (a, b) -> a,
                        LinkedHashMap::new));

        return JobSpecProfile.builder()
                .jobType(jobType)
                .sampleSize(sampleSize)
                .gpaRatios(gpaRatios)
                .toeicEquivalents(toeicEquivalents)
                .certCounts(certCounts)
                .experienceCounts(experienceCounts)
                .certStats(List.copyOf(certStats))
                .containsDemoData(containsDemoData)
                .areaRatios(areaRatios)
                .githubSampleSize(githubSampleSize)
                .build();
    }

    /** 합격자들이 가장 많이 쓴 원본 표기를 화면 표시용으로 고른다. 동률이면 먼저 등장한 표기. */
    private String mostCommonRawForm(Map<String, Integer> rawForms, String fallback) {
        if (rawForms == null || rawForms.isEmpty()) return fallback;
        String best = fallback;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : rawForms.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }
}
