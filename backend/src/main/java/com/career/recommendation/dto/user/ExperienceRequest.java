package com.career.recommendation.dto.user;

import com.career.recommendation.domain.ExperienceArea;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Getter
@Setter
public class ExperienceRequest {

    private static final int MAX_STACK_SIZE = 10;

    // type은 선택 입력이다 — null을 그대로 통과시켜야 toMap()에서 "ETC" 기본값으로 채울 수 있다.
    @Pattern(
            regexp = "(?i)(INTERNSHIP|PROJECT|COMPETITION|EXTERNAL|EDUCATION|ETC)",
            message = "올바른 경험 유형이 아닙니다."
    )
    private String type;

    @NotBlank(message = "경험 제목은 필수입니다.")
    @Size(max = 100, message = "경험 제목은 100자 이하여야 합니다.")
    private String title;

    @Size(max = 500, message = "경험 설명은 500자 이하여야 합니다.")
    private String description;

    // source도 type과 같은 이유로 선택 입력이다 — 미기재/blank는 toMap()에서 "MANUAL" 기본값으로
    // 채운다. GithubAnalysisService가 자동 생성하는 항목만 "GITHUB"를 명시적으로 채워 넣는다.
    @Pattern(
            regexp = "(?i)(MANUAL|GITHUB)",
            message = "올바른 경험 출처가 아닙니다."
    )
    private String source;

    // --- E11(1단계) 신규 필드 — 전부 선택 입력, 점수(percentile)에는 반영하지 않는다 ---

    /** 활동 기간(개월수). GitHub 파생 경험은 첫~마지막 author 커밋 사이 개월수로 자동 채워진다. */
    @Min(value = 1, message = "활동 기간은 1개월 이상이어야 합니다.")
    @Max(value = 120, message = "활동 기간은 120개월 이하여야 합니다.")
    private Integer months;

    @Size(max = 100, message = "역할은 100자 이하여야 합니다.")
    private String role;

    @Size(max = 10, message = "사용 기술은 최대 10개까지 저장할 수 있습니다.")
    private List<@Size(max = 50, message = "기술 이름은 50자 이하여야 합니다.") String> stack;

    /** 허용값은 {@link ExperienceArea} 13종 코드뿐이다 — {@link #isAreasValid()}가 검증한다. */
    private List<String> areas;

    /** 2차(E11 2단계)에서 AI가 채운다 — 이번 단계는 검증·왕복만 담당한다. */
    @Pattern(
            regexp = "(?i)(IMPLEMENTED|CONFIGURED|BOILERPLATE)",
            message = "올바른 구현 깊이가 아닙니다."
    )
    private String depth;

    /**
     * 함수 이름: isAreasValid()
     * 기능: areas 배열의 각 원소가 (공백 제거 후) ExperienceArea 코드와 대소문자 무시하고 일치하는지
     *       검증한다. null·빈 문자열 원소는 toMap()에서 조용히 제거되므로 여기서는 통과시킨다.
     *       areas가 null이거나 모든 원소가 유효하면 true, 알 수 없는 코드가 하나라도 있으면 false이다.
     * 파라미터: 없음
     * 반환값: boolean -> 검증 통과 여부(true: 통과, false: 올바르지 않은 기여 영역 코드 포함)
     */
    @AssertTrue(message = "올바른 기여 영역 코드가 아닙니다.")
    @JsonIgnore
    public boolean isAreasValid() {
        if (areas == null) {
            return true;
        }
        for (String area : areas) {
            if (area == null || area.isBlank()) {
                continue;
            }
            if (ExperienceArea.from(area).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 함수 이름: toMap()
     * 기능: 경험 요청을 JSONB 저장용 Map으로 변환한다. type은 비면 "ETC", source는 비면 "MANUAL"로 채우고
     *       대문자로 정규화한다. 문자열은 trim하며, description·months·role·stack·areas·depth는 값이 있을 때만 넣는다.
     * 파라미터: 없음
     * 반환값: Map<String, Object> -> 정리된 경험 항목(삽입 순서 유지)
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", (type == null || type.isBlank())
                ? "ETC"
                : type.trim().toUpperCase(Locale.ROOT));
        result.put("title", title.trim());

        if (description != null && !description.isBlank()) {
            result.put("description", description.trim());
        }

        result.put("source", (source == null || source.isBlank())
                ? "MANUAL"
                : source.trim().toUpperCase(Locale.ROOT));

        if (months != null) {
            result.put("months", months);
        }

        if (role != null && !role.isBlank()) {
            result.put("role", role.trim());
        }

        List<String> cleanStack = cleanStrings(stack, MAX_STACK_SIZE);
        if (!cleanStack.isEmpty()) {
            result.put("stack", cleanStack);
        }

        List<String> cleanAreas = normalizeAreas(areas);
        if (!cleanAreas.isEmpty()) {
            result.put("areas", cleanAreas);
        }

        if (depth != null && !depth.isBlank()) {
            result.put("depth", depth.trim().toUpperCase(Locale.ROOT));
        }

        return result;
    }

    /**
     * 함수 이름: cleanStrings()
     * 기능: 앞뒤 공백 제거 후 빈 문자열 원소는 버리고, 상한(maxSize)까지만 남긴다.
     * 파라미터: List<String> raw -> 정리할 문자열 목록(null 가능)
     *           int maxSize -> 남길 최대 원소 수
     * 반환값: List<String> -> 정리된 문자열 목록, 입력이 null이면 빈 리스트
     */
    private static List<String> cleanStrings(List<String> raw, int maxSize) {
        if (raw == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : raw) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            result.add(trimmed);
            if (result.size() >= maxSize) {
                break;
            }
        }
        return result;
    }

    /**
     * 함수 이름: normalizeAreas()
     * 기능: ExperienceArea 코드로 정규화(대소문자 무시)하고 중복을 제거한다. 알 수 없는 값은 버린다.
     * 파라미터: List<String> raw -> 정규화할 기여 영역 문자열 목록(null 가능)
     * 반환값: List<String> -> 중복 없는 ExperienceArea 코드 목록(입력 순서 유지), 입력이 null이면 빈 리스트
     */
    private static List<String> normalizeAreas(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            ExperienceArea.from(value).ifPresent(area -> result.add(area.name()));
        }
        return new ArrayList<>(result);
    }
}
