package com.career.recommendation.util;

import com.career.recommendation.entity.Activity;
import com.career.recommendation.entity.PasserData;
import com.career.recommendation.entity.TargetJob;
import com.career.recommendation.entity.UserSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * BE-1 담당 — Gemini 프롬프트에 주입할 데이터를 변환하는 공통 유틸리티.
 *
 * RecommendationService, RoadmapService에서 중복되던 메서드를 이곳으로 통합하여
 * 단일 진실 원천(Single Source of Truth)을 보장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptDataBuilder {

    private final ObjectMapper objectMapper;

    /**
     * DB 활성 활동 목록을 Gemini 프롬프트용 JSON 문자열로 변환한다.
     * 각 활동의 id, type, name, organization, description, deadline, tags 정보를 포함한다.
     */
    public String buildAvailableActivitiesJson(List<Activity> activities) {
        if (activities.isEmpty()) return "[]";
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Activity a : activities) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", a.getId().toString());
                item.put("type", a.getType());
                item.put("name", a.getName());
                item.put("organization", a.getOrganization());
                if (a.getDescription() != null) {
                    String desc = a.getDescription();
                    item.put("description", desc.length() > 100 ? desc.substring(0, 100) + "..." : desc);
                }
                if (a.getDeadline() != null) {
                    item.put("deadline", a.getDeadline().toString());
                }
                if (a.getTags() != null) {
                    item.put("tags", a.getTags());
                }
                // 활동 자격 요건(targetSpec)을 포함하여 AI가 유저 스펙과 비교할 수 있도록 전달
                if (a.getTargetSpec() != null) {
                    item.put("targetSpec", a.getTargetSpec());
                }
                list.add(item);
            }
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("활동 목록 JSON 변환 실패: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * 유저 스펙을 Gemini 프롬프트용 JSON 문자열로 직렬화한다.
     * 추천(F-03)용 — 학점, 학점 만점, 어학, 자격증, 학년 모두 포함.
     */
    public String serializeSpecForRecommendation(UserSpec userSpec) {
        if (userSpec == null) return "{}";
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "gpa", userSpec.getGpa() != null ? userSpec.getGpa() : "없음",
                    "gpaMax", userSpec.getGpaMax() != null ? userSpec.getGpaMax() : 4.5,
                    "languageScores", userSpec.getLanguageScores() != null ? userSpec.getLanguageScores() : List.of(),
                    "certifications", userSpec.getCertifications() != null ? userSpec.getCertifications() : new String[]{},
                    "grade", userSpec.getGrade() != null ? userSpec.getGrade() : "미입력"
            ));
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 유저 스펙을 Gemini 프롬프트용 JSON 문자열로 직렬화한다.
     * 로드맵(F-05)용 — 학점, 학년, 자격증만 포함.
     */
    public String serializeSpecForRoadmap(UserSpec userSpec) {
        if (userSpec == null) return "{}";
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "gpa", userSpec.getGpa() != null ? userSpec.getGpa() : "없음",
                    "grade", userSpec.getGrade() != null ? userSpec.getGrade() : "미입력",
                    "certifications", userSpec.getCertifications() != null ? userSpec.getCertifications() : new String[]{}
            ));
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 유사 합격자 목록을 Gemini 프롬프트용 텍스트로 변환한다.
     */
    public String buildSimilarCasesText(List<PasserData> passerList) {
        if (passerList.isEmpty()) return "유사 합격자 데이터 없음";
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(passerList.size(), 5);
        for (int i = 0; i < limit; i++) {
            PasserData p = passerList.get(i);
            sb.append(String.format("합격자%d: 학점=%s/%s, 어학=%s, 자격증=%s\n",
                    i + 1,
                    p.getGpa() != null ? p.getGpa() : "미상",
                    p.getGpaMax() != null ? p.getGpaMax() : "미상",
                    p.getLanguageScores() != null ? p.getLanguageScores() : List.of(),
                    p.getCertifications() != null ? String.join(", ", p.getCertifications()) : "없음"
            ));
        }
        if (passerList.size() > 5) {
            sb.append(String.format("... 외 %d명\n", passerList.size() - 5));
        }
        return sb.toString();
    }

    /**
     * 목표 직무 정보를 프롬프트용 문자열로 변환한다.
     */
    public String buildTargetJobString(TargetJob targetJob) {
        if (targetJob == null) return "미설정";
        // companySize·industry는 선택 입력이라 null일 수 있다(TargetJobRequest에 @NotBlank가
        // 없다). null 폴백 없이 그대로 이어붙이면 문자열 결합 규칙상 "BACKEND / null / null"이
        // 되어 그 리터럴 "null"이 Gemini 프롬프트에 그대로 들어간다 — 다른 필드들처럼
        // "미설정"으로 폴백한다.
        String companySize = targetJob.getCompanySize() != null ? targetJob.getCompanySize() : "미설정";
        String industry = targetJob.getIndustry() != null ? targetJob.getIndustry() : "미설정";
        return targetJob.getJobType() + " / " + companySize + " / " + industry;
    }
}
