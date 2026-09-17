package com.career.recommendation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BE-1 담당 — Gemini API 연동 서비스
 * 추천 생성 및 로드맵 생성 프롬프트를 관리하며 Google Gemini API를 호출합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.key-in-header:true}")
    private boolean keyInHeader;

    @Value("${gemini.api.base-url}")
    private String baseUrl;

    @Value("${gemini.api.model}")
    private String model;

    // gemini-2.5-flash는 기본적으로 thinking이 켜져 있어, 화면에 보이지 않는 thinking 토큰까지
    // output으로 과금될 수 있다. 이 서비스의 모든 응답은 JSON 필드 몇 개만 뽑아내는 짧은 구조라
    // thinking이 답변 품질에 크게 기여하지 않는다고 보고 기본을 0(끔)으로 둔다.
    @Value("${gemini.api.max-output-tokens:4096}")
    private int maxOutputTokens;

    private final WebClient.Builder webClientBuilder;
    private final GeminiDailyQuota dailyQuota;
    private final GeminiCallStats callStats;

    /** JSON 블록만 추출하는 패턴 (응답 앞뒤 잡담 제거) */
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[\\s\\S]*}", Pattern.DOTALL);

    /**
     * 활동 추천 생성 (DB 활동 기반 RAG 패턴)
     * @param userSpecJson          사용자 스펙 (JSON 문자열)
     * @param targetJob             목표 직무
     * @param positionContext       합격자 비교 데이터(직무 요구 프로필 내 위치·갭 요약 — PromptDataBuilder.buildPositionContextText)
     * @param availableActivitiesJson DB에 등록된 활성 활동 목록 (JSON 배열 문자열)
     * @return Gemini API 응답 JSON 텍스트 (파싱 실패 시 빈 문자열)
     */
    public String generateRecommendation(String userSpecJson, String targetJob,
                                         String positionContext, String availableActivitiesJson,
                                         java.time.LocalDate today) {
        String prompt = buildRecommendationPrompt(userSpecJson, targetJob, positionContext, availableActivitiesJson, today);
        String systemInstruction = "당신은 취업 커리어 어드바이저입니다. 사용자의 스펙을 분석하고, 제공된 활동 목록 중에서만 맞춤형 활동을 추천해 주세요. 목록에 없는 활동을 임의로 만들지 마세요. 반드시 JSON 형식으로만 응답하세요.";
        String raw = callGeminiApi(systemInstruction, prompt);
        return extractJsonBlock(raw);
    }

    /**
     * 커리어 로드맵 생성 (DB 활동 기반 RAG 패턴 + F-03 연계 및 합격자 비교 맥락 반영)
     * @param userSpecJson               사용자 스펙 JSON
     * @param targetJob                  목표 직무
     * @param grade                      현재 학년 (null이면 학기 구분 없이 월별 단위)
     * @param positionContext            합격자 비교 데이터(직무 요구 프로필 내 위치·갭 요약)
     * @param topRecommendedActivities  F-03에서 우선 추천된 활동 목록 요약 (JSON 문자열)
     * @param availableActivitiesJson     DB에 등록된 활성 활동 목록 (JSON 배열 문자열)
     */
    public String generateRoadmap(String userSpecJson, String targetJob, Integer grade,
                                  String positionContext, String topRecommendedActivities,
                                  String availableActivitiesJson, java.time.LocalDate today) {
        String prompt = buildRoadmapPrompt(userSpecJson, targetJob, grade, positionContext, topRecommendedActivities, availableActivitiesJson, today);
        // ⚠️ 이 systemInstruction은 buildRoadmapPrompt의 규칙 4·5와 반드시 같은 매칭 정책을
        // 말해야 한다. 예전엔 여기서 "단기 기간에는 DB 활동 매칭, 먼 미래는 가이드 제안"이라는
        // 2분할을 못박아 규칙 4("각 시기마다 매칭")·5("적합한 공고가 없는 시기만 가이드")와
        // 모순됐다 — Gemini는 systemInstruction을 유저 턴만큼 무겁게 반영하므로, 3~6개월
        // 구간에 실제로 열려 있는 DB 활동이 있어도 매칭을 건너뛰고 가이드만 낼 위험이 있었다
        // (Opus 5 높음 검토 12라운드가 규칙 4·5만 고친 이전 수정이 이 문장 때문에 절반만
        // 효과가 있었을 거라고 지적). 규칙 4·5와 동일하게 "시기 무관, 적합한 활동이 있으면
        // 매칭, 없는 시기만 가이드"로 통일한다.
        String systemInstruction = "당신은 취업 커리어 어드바이저입니다. 사용자의 현재 스펙과 목표 직무, 합격자 비교 데이터(분포 내 위치·갭) 및 우선 추천 활동을 기반으로 시기별 커리어 로드맵을 생성하되, 각 시기마다 제공된 DB 활동 목록 중 마감일과 직무가 적합한 활동이 있으면 매칭하고, 적합한 공고가 없는 시기에만 역량 준비 가이드를 제안하세요. JSON 형식으로만 응답하세요.";
        String raw = callGeminiApi(systemInstruction, prompt);
        return extractJsonBlock(raw);
    }

    private String buildRecommendationPrompt(String spec, String job, String cases, String availableActivities,
                                             java.time.LocalDate today) {
        // ⚠️ 오늘 날짜를 명시하지 않으면 Gemini는 학습 시점의 날짜 감각으로 마감일의 임박도를
        // 추측한다 — "마감이 임박한/여유 있는 활동" 판단이 실제 오늘과 어긋날 수 있다.
        return String.format("""
                [오늘 날짜]
                %s

                [사용자 현재 스펙]
                %s

                [목표 직무]
                %s

                [합격자 비교 데이터 — 분포 내 위치와 갭]
                %s

                [추천 가능한 활동 목록 (DB 등록 활동)]
                %s

                ## 규칙
                1. 반드시 위 "추천 가능한 활동 목록"에 있는 활동 중에서만 선택하세요.
                2. 각 활동의 id 값을 그대로 사용하세요 (UUID 형식). 목록에 없는 ID는 절대로 임의로 생성하지 마세요.
                3. 사용자의 스펙과 목표 직무에 가장 적합한 활동을 최대 5개 골라 추천하세요.
                   특히 [합격자 비교 데이터]의 갭(합격자 다수가 보유하지만 사용자에게 없는 항목)을
                   메울 수 있는 활동을 우선하세요.
                4. 목록에 적합한 활동이 5개 미만이면 있는 만큼만 추천하세요.
                5. targetSpec은 공고의 필수 지원 조건입니다. 사용자가 명확하게 충족하지 못하는 활동은 우선 추천하지 마세요.
                6. UserSpec에 해당 정보가 없으면 불충족으로 단정하지 말고 '확인 필요'로 처리하세요.
                7. 우대사항은 targetSpec에 포함되지 않습니다.
                8. UserSpec에 없는 경력·전공·학력 정보는 임의로 생성하지 마세요.
                9. 각 추천에는 id(UUID), name, type, reason(이 사용자에게 추천하는 구체적 이유), deadline(YYYY-MM-DD), targetGap 필드를 포함하세요.
                   targetGap은 이 활동이 메우는 갭으로, [합격자 비교 데이터]의 "targetGap에 쓸 수 있는 갭 이름" 중 하나를 글자 그대로 쓰거나, 해당 없으면 null로 두세요. 목록에 없는 이름을 만들지 마세요.
                10. 추천 이유(reason)에는 사용자가 충족한 조건과 확인이 필요한 조건을 구분하여 작성하세요.
                11. 응답은 {"activities": [...]} JSON 형식으로만 출력하세요.
                """, today, spec, job, cases, availableActivities);
    }

    private String buildRoadmapPrompt(String spec, String job, Integer grade,
                                      String cases, String topRecommended, String availableActivities,
                                      java.time.LocalDate today) {
        // ⚠️ periodGuide에 오늘 날짜를 반드시 명시한다. 예전엔 날짜 없이 학기/방학 구분
        // 규칙만 줘서, Gemini가 타임라인의 "시작 시기"를 활동 마감일 등에서 추측했다 —
        // 2학년 사용자의 로드맵이 8월 요청인데도 엉뚱한 학기에서 시작하거나, 6개월 창을
        // 벗어난 시기까지 늘어지는 원인이었다(2026-08-11 사용자 제보: "로드맵이 3학년
        // 1학기까지밖에 안 나온다" — 시작 앵커가 없으니 끝 앵커도 흔들린 것).
        String periodGuide = (grade != null)
                ? String.format("""
                오늘은 %s이고, 사용자는 현재 %d학년입니다. 기간 구분은 반드시 "학기"와 "방학"을 기준으로 나눠주세요.
                예시: "3학년 2학기 (9~11월)", "겨울방학 (12월~2월)", "4학년 1학기 (3~6월)" 등.
                타임라인의 첫 번째 시기는 오늘이 속한 학기/방학이어야 하고, 마지막 시기는 오늘로부터 6개월 이내여야 합니다.
                """, today, grade)
                : String.format("""
                오늘은 %s입니다. 기간은 월 단위로 나눠주세요. 예: "7월", "8~9월" 등.
                타임라인은 오늘이 속한 달부터 시작해 6개월 이내로 구성하세요.
                """, today);

        return String.format("""
                [사용자 현재 스펙]
                %s

                [목표 직무]
                %s

                [합격자 비교 데이터 — 분포 내 위치와 갭]
                %s

                %s

                [우선 반영할 AI 추천 활동 (F-03 결과)]
                %s
                
                [전체 DB 등록 활동 목록]
                %s
                
                ## 규칙
                1. 6개월 커리어 로드맵을 위 기간 단위로 작성해 주세요.
                2. [합격자 비교 데이터]의 갭(부족한 항목)을 이른 시기부터 우선 보완하는 방향으로 흐름을 구성하세요. "targetGap에 쓸 수 있는 갭 이름"의 순서가 보완 우선순위입니다 — 앞에 있는 갭을 더 이른 시기에 배치하세요.
                3. [우선 반영할 AI 추천 활동]에 포함된 활동들을 6개월 타임라인 중 적절한 시기에 우선적으로 배치하세요.
                4. 각 시기마다 [전체 DB 등록 활동 목록]에서 마감일과 직무가 적합한 실제 활동의 ID를 매칭하세요.
                5. 적합한 DB 활동 공고가 없거나 마감된 시기는, activityIds는 빈 배열([])로 두고, 해당 시기에 필수적으로 준비해야 할 역량 개발 가이드(예: "자격증 취득 및 포트폴리오 구체화", "알고리즘 코딩테스트 대비", "주요 부스트캠프/인턴십 차기 기수 모집 대비")를 activity 필드와 reason 필드에 설명하세요.
                6. DB 활동을 매칭할 때 각 활동의 id 값을 그대로 사용하세요 (UUID 형식). 목록에 없는 ID는 절대로 임의로 만들지 마세요.
                7. 각 시기에 최대 3개의 활동 또는 준비 가이드를 추천하세요.
                8. 각 항목에는 period(시기 설명), priority(HIGH/MEDIUM/LOW), activity(활동명 또는 역량 준비 가이드 텍스트), reason(이유), activityIds(UUID 배열, 적합한 DB 활동이 없는 경우 빈 배열 []) 필드를 포함하세요.
                   ⚠️ priority 규칙: 가장 이른 시기(당장 시작할 단기 단계)에만 "HIGH"(화면 표기: "지금 집중")를 부여하세요. 그 다음 시기에는 "MEDIUM"("중요")을, 그 이후의 나머지 모든 시기에는 "LOW"("준비")를 부여하세요. (학년이 없으면 월 단위로 4~6개 시기가 나올 수 있습니다 — 시기 개수와 무관하게 이 규칙을 적용하세요.)
                9. 응답은 {"timeline": [...]} JSON 형식으로만 출력하세요.
                """, spec, job, cases, periodGuide, topRecommended, availableActivities);
    }

    /**
     * E11-6 — 경험 입력 내용을 바탕으로 기술적 깊이를 판별할 후속 질문 2~3개를 생성한다.
     * @param experienceContext 경험 항목(유형·제목·설명·역할·기술스택·현재 태그된 영역)을 요약한 텍스트
     * @return Gemini 응답 JSON 텍스트 {"questions": [...]}. 실패 시 빈 문자열(호출부가 폴백 처리).
     */
    public String generateExperienceQuestions(String experienceContext) {
        String prompt = buildExperienceQuestionsPrompt(experienceContext);
        String systemInstruction = "당신은 취업 커리어 어드바이저입니다. 사용자가 입력한 경험 항목의 기술적 깊이를 " +
                "판별할 수 있는 한국어 후속 질문을 만드세요. 반드시 JSON 형식으로만 응답하세요.";
        String raw = callGeminiApi(systemInstruction, prompt);
        return extractJsonBlock(raw);
    }

    private String buildExperienceQuestionsPrompt(String experienceContext) {
        return String.format("""
                [경험 정보]
                %s

                ## 규칙
                1. 이 경험이 실제로 어느 수준까지 구현되었는지(직접 설계·구현했는지, 설정·연동 위주인지,
                   반복적인 기본 작업 수준인지)를 판별할 수 있는 후속 질문을 한국어로 2~3개 작성하세요.
                2. 질문은 이 경험의 구체적인 내용(제목·설명·역할·기술스택)에 기반해야 합니다. "어떤 역할을
                   맡았나요?" 같은 범용 질문은 피하세요.
                3. 응답은 {"questions": ["...", "..."]} JSON 형식으로만 출력하세요.
                """, experienceContext);
    }

    /**
     * E11-6 — 경험과 후속 질문 답변을 바탕으로 기여 영역·구현 깊이·역할 요약을 판정한다.
     * 답변 원문은 이 호출 이후 어디에도 저장하지 않는다(호출부 책임).
     * @param experienceContext 경험 항목 요약 텍스트
     * @param qaContext         후속 질문과 답변 목록을 요약한 텍스트
     * @return Gemini 응답 JSON 텍스트 {"areas": [...], "depth": "...", "roleSummary": "..."}.
     *         실패 시 빈 문자열(호출부가 폴백 처리).
     */
    public String generateExperienceEnrichment(String experienceContext, String qaContext) {
        String prompt = buildExperienceEnrichPrompt(experienceContext, qaContext);
        String systemInstruction = "당신은 취업 커리어 어드바이저입니다. 사용자의 경험과 후속 질문 답변을 바탕으로 " +
                "이 경험이 다룬 기여 영역과 구현 깊이, 역할 요약을 판정하세요. 반드시 JSON 형식으로만 응답하세요.";
        String raw = callGeminiApi(systemInstruction, prompt);
        return extractJsonBlock(raw);
    }

    private String buildExperienceEnrichPrompt(String experienceContext, String qaContext) {
        return String.format("""
                [경험 정보]
                %s

                [후속 질문과 답변]
                %s

                ## 규칙
                1. areas는 다음 코드 중에서만 골라 배열로 반환하세요(해당 없으면 빈 배열): AUTH, API, DB,
                   CI_CD, TEST, UI, STATE_MGMT, DATA_PIPELINE, ML_MODEL, INFRA, DOCS, SECURITY, PLANNING.
                2. depth는 IMPLEMENTED(직접 설계·구현), CONFIGURED(설정·연동 위주), BOILERPLATE(반복적인
                   기본 작업) 중 하나여야 합니다.
                3. roleSummary는 이 경험에서 사용자가 실제로 한 일을 100자 이내 한국어 한 문장으로 요약하세요.
                4. 답변에서 확인되지 않는 내용은 지어내지 마세요.
                5. 응답은 {"areas": [...], "depth": "...", "roleSummary": "..."} JSON 형식으로만 출력하세요.
                """, experienceContext, qaContext);
    }

    /**
     * Gemini API 원문 응답에서 순수 JSON 블록만 추출한다.
     */
    public String extractJsonBlock(String raw) {
        if (raw == null || raw.isBlank()) return "";
        Matcher matcher = JSON_PATTERN.matcher(raw);
        return matcher.find() ? matcher.group() : "";
    }

    private String callGeminiApi(String systemInstruction, String userMessage) {
        WebClient client = webClientBuilder
                .baseUrl(baseUrl)
                .build();

        // Gemini generateContent API 규격에 맞춘 요청 Body 생성
        Map<String, Object> requestBody = Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))
                ),
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("text", userMessage))
                        )
                ),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        // thinkingBudget: 0 — 보이지 않는 thinking 토큰 과금을 막는다(gemini-2.5-flash는
                        // 기본 thinking이 켜져 있다). maxOutputTokens는 이 서비스의 모든 호출(추천·로드맵·
                        // 후속 질문·경험 enrich) 공통 상한 — 어느 응답도 4096 토큰을 넘길 필요가 없다.
                        "thinkingConfig", Map.of("thinkingBudget", 0),
                        "maxOutputTokens", maxOutputTokens
                )
        );

        if (apiKey == null || apiKey.isBlank() || apiKey.contains("입력")) {
            log.warn("Gemini API 키가 설정되지 않았습니다. 즉시 폴백 데이터를 반환합니다.");
            return "";
        }
        if (!dailyQuota.tryAcquire()) {
            // 전역 일일 상한 — 호출을 건너뛰면 호출부가 폴백 추천으로 처리한다(사용자별 한도와 별개).
            return "";
        }

        // API 키는 x-goog-api-key 헤더로 보낸다(Gemini 공식 지원). 예전엔 쿼리스트링(?key=)에 실어서 프록시·APM·
        // WebClientResponseException 메시지(요청 URL 포함)에 키가 찍힐 수 있었다. 운영에서 헤더 방식에 문제가 보이면
        // GEMINI_API_KEY_IN_HEADER=false 로 되돌릴 수 있다 — 두 방식 모두 같은 키로 동작한다.
        String uri = keyInHeader
                ? String.format("/models/%s:generateContent", model)
                : String.format("/models/%s:generateContent?key=%s", model, apiKey);

        long startedAt = System.currentTimeMillis();
        try {
            Map<?, ?> response = client.post()
                    .uri(uri)
                    .headers(h -> { if (keyInHeader) h.set("x-goog-api-key", apiKey); })
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(java.time.Duration.ofSeconds(60));

            if (response != null && response.get("candidates") instanceof List<?> candidates && !candidates.isEmpty()) {
                Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
                if (candidate.get("content") instanceof Map<?, ?> content && content.get("parts") instanceof List<?> parts && !parts.isEmpty()) {
                    Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
                    callStats.recordSuccess(System.currentTimeMillis() - startedAt);
                    return (String) firstPart.get("text");
                }
            }
            // 200이지만 candidates가 비어 있는 응답(안전 필터 등) — 호출부에서 폴백을 타므로 실패로 센다.
            callStats.recordFailure(System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            callStats.recordFailure(System.currentTimeMillis() - startedAt);
            log.error("Gemini API 호출 실패: {}", e.getMessage());
        }
        return "";
    }
}
