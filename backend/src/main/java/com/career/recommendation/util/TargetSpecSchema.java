package com.career.recommendation.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Activity.targetSpec(JSONB, Map&lt;String, Object&gt;)의 허용 키·값 타입을 고정한다.
 *
 * 크롤러(linkareer_crawler.py의 extract_required_target_spec)가 유일한 생산자이고, 현재
 * 실제로 채우는 키는 "required_qualifications"(문자열 리스트, 최대 20개) 하나뿐이다 — 우대사항은
 * 애초에 저장하지 않는다. GraduateOnlyActivityFilter·PromptDataBuilder는 키를 가리지 않고
 * targetSpec 전체를 재귀적으로 훑거나 그대로 프롬프트에 싣기 때문에, 스키마를 벗어나도 즉시
 * 기능이 깨지지는 않는다 — 그래서 이 검증은 런타임 저장 경로에 강제하지 않고, 크롤러 시드가
 * 실제로 이 형태를 벗어나지 않았는지 테스트로만 고정해 둔다.
 */
public final class TargetSpecSchema {

    private static final Set<String> ALLOWED_KEYS = Set.of("required_qualifications");

    private TargetSpecSchema() {
    }

    /**
     * targetSpec의 각 키가 허용 목록에 있고 값 타입이 맞는지 검사한다.
     * null·빈 맵은 위반이 아니다(자격 요건을 못 찾은 활동은 {}로 저장된다).
     *
     * @return 위반 사유 목록. 문제가 없으면 빈 리스트.
     */
    public static List<String> validate(Map<String, Object> targetSpec) {
        List<String> violations = new ArrayList<>();
        if (targetSpec == null || targetSpec.isEmpty()) {
            return violations;
        }
        for (Map.Entry<String, Object> entry : targetSpec.entrySet()) {
            String key = entry.getKey();
            if (!ALLOWED_KEYS.contains(key)) {
                violations.add("알 수 없는 키: " + key);
                continue;
            }
            if (!isStringList(entry.getValue())) {
                violations.add(key + "은(는) 문자열 리스트여야 합니다.");
            }
        }
        return violations;
    }

    private static boolean isStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return false;
        }
        return list.stream().allMatch(item -> item instanceof String);
    }
}
