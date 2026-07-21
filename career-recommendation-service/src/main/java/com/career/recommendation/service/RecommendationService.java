package com.career.recommendation.service;

import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.dto.recommendation.RecommendationResponse.ActivityItem;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.UserSpec;
import com.career.recommendation.repository.PasserDataRepository;
import com.career.recommendation.repository.UserSpecRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * BE-1 담당 — F-03 활동 추천 서비스
 *
 * 흐름:
 * 1. 사용자 UserSpec 조회
 * 2. 유사 합격자 검색 (GPA ±0.3 범위)
 * 3. 합격자 데이터 충분 → PasserData 기반 Claude 호출
 *    합격자 데이터 부족 → AI 일반 추천 (Claude 단독 호출)
 * 4. JSON 방어 파싱 (ClaudeResponseParser 3단계)
 * 5. matchScore 계산 후 응답 조립
 *
 * TODO (7/24 주간):
 *   - Spring Cache(@Cacheable) 연동
 *   - TargetJob Entity 연동 (BE-2 완료 후)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final UserSpecRepository       userSpecRepository;
    private final PasserDataRepository     passerDataRepository;
    private final ClaudeService            claudeService;
    private final ClaudeResponseParser     responseParser;
    private final MatchScoreCalculator     matchScoreCalculator;

    // 유사 합격자 GPA 검색 범위 (±)
    private static final BigDecimal GPA_TOLERANCE = new BigDecimal("0.3");

    // 추천 fallback JSON
    private static final String RECOMMENDATION_FALLBACK = """
            {"activities": [
              {"name":"삼성 청년 SW 아카데미(SSAFY)", "type":"EXTERNAL",
               "reason":"국내 최대 규모 SW 교육 프로그램으로 취업 연계율이 높습니다.",
               "matchScore":70, "deadline":null, "url":"https://www.ssafy.com"},
              {"name":"우아한테크코스", "type":"EXTERNAL",
               "reason":"실무 중심 교육으로 백엔드 역량을 집중 강화할 수 있습니다.",
               "matchScore":65, "deadline":null, "url":"https://woowacourse.github.io"},
              {"name":"카카오 인턴십", "type":"INTERNSHIP",
               "reason":"대기업 인턴 경험은 이력서에서 큰 강점이 됩니다.",
               "matchScore":60, "deadline":null, "url":"https://kakao.com/careers"},
              {"name":"공개 SW 개발자 대회", "type":"COMPETITION",
               "reason":"오픈소스 기여 경험과 수상 이력을 동시에 쌓을 수 있습니다.",
               "matchScore":55, "deadline":null, "url":"https://www.oss.kr"},
              {"name":"42서울", "type":"EXTERNAL",
               "reason":"자기주도 학습과 협업 능력을 키울 수 있는 특색 있는 프로그램입니다.",
               "matchScore":50, "deadline":null, "url":"https://42seoul.kr"}
            ]}
            """;

    /**
     * 활동 추천 실행 (F-03)
     *
     * @param userId    인증된 사용자 ID
     * @param targetJob 목표 직무 문자열
     * @return 추천 결과
     */
    public RecommendationResponse recommend(UUID userId, String targetJob) {
        // 1. 사용자 스펙 조회
        UserSpec userSpec = userSpecRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("스펙 프로필이 없습니다. 먼저 스펙을 입력해주세요."));

        // 2. 유사 합격자 검색
        List<PasserData> similarPassers = findSimilarPassers(userSpec);
        log.info("유사 합격자 검색 결과: userId={}, count={}", userId, similarPassers.size());

        // 3. 데이터 충분 여부에 따른 분기
        String userSpecJson = toSpecJson(userSpec);

        if (matchScoreCalculator.isDataSufficient(similarPassers.size())) {
            return recommendWithPasserData(userSpec, userSpecJson, targetJob, similarPassers);
        } else {
            return recommendAiOnly(userSpecJson, targetJob);
        }
    }

    // ─── 합격자 데이터 기반 추천 ──────────────────────────────────────────────

    private RecommendationResponse recommendWithPasserData(UserSpec userSpec,
                                                            String userSpecJson,
                                                            String targetJob,
                                                            List<PasserData> passers) {
        String passerCasesJson = buildPasserCasesJson(passers);
        String rawResponse = claudeService.generateRecommendation(userSpecJson, targetJob, passerCasesJson);

        JsonNode parsed = responseParser.parse(rawResponse, "activities", RECOMMENDATION_FALLBACK);
        List<ActivityItem> items = extractActivityItems(parsed, userSpec, passers);

        return RecommendationResponse.withPasserData(items, passers.size());
    }

    // ─── AI 단독 추천 (합격자 데이터 부족 시) ────────────────────────────────

    private RecommendationResponse recommendAiOnly(String userSpecJson, String targetJob) {
        String rawResponse = claudeService.generateRecommendation(userSpecJson, targetJob, "데이터 없음");

        JsonNode parsed = responseParser.parse(rawResponse, "activities", RECOMMENDATION_FALLBACK);
        List<ActivityItem> items = extractActivityItems(parsed, null, List.of());

        return RecommendationResponse.aiOnly(items);
    }

    // ─── 유사 합격자 검색 ────────────────────────────────────────────────────

    private List<PasserData> findSimilarPassers(UserSpec userSpec) {
        if (userSpec.getGpa() == null) {
            return passerDataRepository.findByIsVerifiedTrue();
        }
        BigDecimal minGpa = userSpec.getGpa().subtract(GPA_TOLERANCE);
        BigDecimal maxGpa = userSpec.getGpa().add(GPA_TOLERANCE);
        return passerDataRepository.findSimilarByGpa(minGpa, maxGpa);
    }

    // ─── JSON 변환 헬퍼 ──────────────────────────────────────────────────────

    private String toSpecJson(UserSpec spec) {
        // 직접 JSON 문자열 조합 (ObjectMapper 불필요 수준의 단순 구조)
        String gpa   = spec.getGpa() != null ? spec.getGpa().toPlainString() : "미입력";
        String lang  = spec.getLanguageScore() != null ? spec.getLanguageScore().toString() : "미입력";
        String certs = spec.getCertifications() != null
                ? String.join(", ", spec.getCertifications()) : "없음";
        String exps  = spec.getExperiences() != null
                ? String.join(", ", spec.getExperiences()) : "없음";

        return """
                {"gpa":"%s","languageScore":"%s","certifications":"%s","experiences":"%s"}
                """.formatted(gpa, lang, certs, exps).trim();
    }

    private String buildPasserCasesJson(List<PasserData> passers) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < passers.size(); i++) {
            PasserData p = passers.get(i);
            sb.append("{")
              .append("\"gpa\":").append(p.getGpa() != null ? p.getGpa() : "null").append(",")
              .append("\"experienceCount\":").append(p.getExperienceCount()).append(",")
              .append("\"summary\":\"").append(p.getSpecSummary() != null ? p.getSpecSummary() : "").append("\"")
              .append("}");
            if (i < passers.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private List<ActivityItem> extractActivityItems(JsonNode root,
                                                     UserSpec userSpec,
                                                     List<PasserData> passers) {
        List<ActivityItem> result = new ArrayList<>();
        JsonNode activities = root.get("activities");
        if (activities == null || !activities.isArray()) return result;

        for (JsonNode node : activities) {
            // Claude가 준 matchScore 우선, 없으면 직접 계산 (합격자 첫 번째 기준)
            int score = node.has("matchScore") ? node.get("matchScore").asInt(50) : 50;
            if (userSpec != null && !passers.isEmpty()) {
                PasserData first = passers.get(0);
                score = matchScoreCalculator.calculate(
                        userSpec,
                        first.getGpa(),
                        first.getLanguageScore(),
                        first.getCertifications(),
                        first.getExperienceCount()
                );
            }

            // tags: Claude가 배열로 줄 수도 있고, 없으면 type 기반 기본값 사용
            String[] tags = extractTags(node);

            result.add(new ActivityItem(
                    null,                              // id: DB 저장 전 null (Activity 매핑은 추후)
                    getText(node, "type"),
                    getText(node, "name"),
                    getText(node, "organization"),     // 주관 기관 (Claude 응답에 포함 시)
                    node.has("deadline") && !node.get("deadline").isNull()
                            ? node.get("deadline").asText() : null,
                    score,
                    getText(node, "reason"),
                    tags
            ));
        }
        return result;
    }

    private String getText(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText("") : "";
    }

    /**
     * Claude 응답에서 tags 배열 추출.
     * 없으면 type 기반 기본 태그 반환.
     */
    private String[] extractTags(JsonNode node) {
        if (node.has("tags") && node.get("tags").isArray()) {
            List<String> tagList = new ArrayList<>();
            node.get("tags").forEach(t -> tagList.add(t.asText()));
            return tagList.toArray(new String[0]);
        }
        // type 기반 기본 태그
        String type = getText(node, "type");
        return switch (type.toUpperCase()) {
            case "INTERNSHIP"  -> new String[]{"인턴십"};
            case "EXTERNAL"    -> new String[]{"대외활동"};
            case "COMPETITION" -> new String[]{"공모전"};
            default            -> new String[]{};
        };
    }
}
