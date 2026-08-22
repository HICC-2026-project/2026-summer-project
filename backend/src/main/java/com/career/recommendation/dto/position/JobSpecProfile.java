package com.career.recommendation.dto.position;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * BE-1 담당 — 직무별 합격자 요구 프로필. 새 계산 체계의 중심 데이터 구조.
 *
 * "유사 합격자 Top 5를 검색해서 평균과 비율 비교"하던 예전 구조를 대체한다:
 * 직무 합격자 <b>전원</b>의 분포를 미리 집계해 두고, 사용자에게는 이 분포 안에서의
 * percentile 위치와 갭(합격자 다수가 가진 것 중 사용자에게 없는 것)을 보여준다.
 *
 * 전원 집계를 쓰는 이유는 예전 globalCertPool·jobPasserCertRows와 같다 — Top N은
 * 스펙을 살짝만 고쳐도 구성이 바뀌어 결과가 요동치지만, 직무 전체 분포는 데이터가
 * 추가될 때만 바뀐다. 요청 시점 계산이 아니라 캐시 가능한 집계값이기도 하다.
 *
 * 분포 배열은 전부 <b>오름차순 정렬</b>되어 있다(SpecPositionCalculator의 percentile
 * 계산이 이 정렬을 전제한다). 각 배열의 길이가 곧 "그 축 데이터를 가진 합격자 수"이며,
 * 결측 합격자는 배열에서 빠진다 — 결측을 0으로 끼워 넣으면 분포가 실제보다 아래로
 * 왜곡된다(v7의 "결측은 중립" 원칙을 분포에도 적용).
 * 단, 자격증 개수(certCounts)는 예외로 전원이 들어간다 — 자격증 0개는 결측이 아니라
 * "실제로 안 가짐"이기 때문이다.
 */
@Getter
@Builder
public class JobSpecProfile {

    /** 프로필의 기준 직무. null이면 직무 구분 없는 전체 합격자 프로필(폴백용). */
    private final String jobType;

    /** 프로필에 포함된 합격자 수. 자격증 보유율의 분모이자 표본 신뢰도 판단 기준. */
    private final int sampleSize;

    /** 정규화 학점 비율(gpa/gpaMax) 분포. 오름차순, 학점 보유 합격자만. */
    private final double[] gpaRatios;

    /** 토익 환산 최고점 분포. 오름차순, 어학 보유(환산 가능) 합격자만. */
    private final double[] toeicEquivalents;

    /** 1인당 자격증 개수(정규화 후 중복 제거) 분포. 오름차순, 0개 합격자 포함 전원. */
    private final int[] certCounts;

    /** 경험 개수 분포. 오름차순, experienceCount가 null인 행만 제외. */
    private final int[] experienceCounts;

    /** 자격증별 보유 통계. 보유율 내림차순 — 갭 리스트가 위에서부터 자른다. */
    private final List<CertStat> certStats;

    /**
     * 프로필에 합성 DEMO(또는 출처 미상) 데이터가 한 건이라도 포함되었는지.
     * FE가 "데모 데이터 포함 비교" 고지를 띄우는 근거다 — 실데이터만으로 집계된 것처럼
     * 보여주면 안 된다는 기존 원칙(sampleComparisonData)을 프로필 체계에서도 유지한다.
     */
    private final boolean containsDemoData;

    /**
     * 자격증 하나의 프로필 내 통계.
     * displayName은 합격자들이 실제로 가장 많이 쓴 원본 표기다 — canonical 값
     * ("AWSSAA", "ADSP")을 그대로 화면에 내보내면 사용자가 못 알아본다.
     */
    @Getter
    @Builder
    public static class CertStat {
        private final String canonicalName;
        private final String displayName;
        private final int holders;
        /** holders / sampleSize. 분모는 자격증 0개 합격자를 포함한 전원이다. */
        private final double holderRate;
    }

    /** canonical 표기의 자격증을 이 프로필 합격자 중 1명 이상이 보유하는가. */
    public boolean containsCert(String canonicalName) {
        return certStats != null && certStats.stream()
                .anyMatch(stat -> stat.getCanonicalName().equals(canonicalName));
    }
}
