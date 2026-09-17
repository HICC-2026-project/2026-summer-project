package com.career.recommendation.util;

import com.career.recommendation.entity.Activity;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 재학생(1~3학년)에게 지원 자격이 없는 "대졸 공채"류 활동을 후보에서 미리 제외한다.
 *
 * 실사용 피드백(2026-09-17): 3학년 사용자에게 "학사 학위 이상/졸업예정자 전용" 공고가 추천·로드맵에
 * 노출됐다. 추천(F-03)과 로드맵(F-05)이 각자 activityRepository.findRecommendableActivities(...)로
 * 후보를 뽑은 뒤 이 필터를 거치게 해, 판정 로직을 한 곳에서만 관리한다.
 *
 * 판정 기준:
 *  - grade가 1~3일 때만 적용한다. null(미입력)·4 이상(졸업예정자 포함)은 지원 가능하므로 거르지 않는다.
 *  - Activity.targetSpec(required_qualifications 포함, 중첩 Map/List까지) 안의 문자열 중 하나라도
 *    "학사"·"대졸"·"대학졸업"·"기졸업자"를 포함하면 제외한다.
 *  - "고등학교 졸업이상"·"학력무관"·"학력·전공 무관" 같은 문구는 위 4개 키워드를 포함하지 않으므로
 *    걸리지 않는다(부분 문자열 매칭이라 키워드 자체가 없으면 오탐이 없다).
 */
public final class GraduateOnlyActivityFilter {

    private static final List<String> GRADUATE_ONLY_KEYWORDS =
            List.of("학사", "대졸", "대학졸업", "기졸업자");

    private GraduateOnlyActivityFilter() {
    }

    /**
     * grade가 1~3인 재학생에게 지원 불가한(대졸 공채류) 활동을 목록에서 제외한다.
     * grade가 null이거나 4 이상이면 원본 목록을 그대로 돌려준다.
     */
    public static List<Activity> filterForGrade(List<Activity> activities, Integer grade) {
        if (activities == null || activities.isEmpty()) {
            return activities;
        }
        if (grade == null || grade < 1 || grade > 3) {
            return activities;
        }
        return activities.stream()
                .filter(a -> !isGraduateOnly(a))
                .toList();
    }

    private static boolean isGraduateOnly(Activity activity) {
        Map<String, Object> targetSpec = activity.getTargetSpec();
        if (targetSpec == null || targetSpec.isEmpty()) {
            return false;
        }
        return containsGraduateOnlyKeyword(targetSpec);
    }

    /** targetSpec 값(문자열·Map·List가 섞여 있을 수 있음)을 재귀적으로 훑어 키워드를 찾는다. */
    private static boolean containsGraduateOnlyKeyword(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            for (String keyword : GRADUATE_ONLY_KEYWORDS) {
                if (s.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object v : map.values()) {
                if (containsGraduateOnlyKeyword(v)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Collection<?> collection) {
            for (Object v : collection) {
                if (containsGraduateOnlyKeyword(v)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }
}
