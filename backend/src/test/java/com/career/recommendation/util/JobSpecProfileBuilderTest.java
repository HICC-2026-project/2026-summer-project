package com.career.recommendation.util;

import com.career.recommendation.dto.position.JobSpecProfile;
import com.career.recommendation.dto.position.JobSpecProfile.CertStat;
import com.career.recommendation.entity.PasserData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JobSpecProfileBuilderTest {

    private final JobSpecProfileBuilder builder = new JobSpecProfileBuilder();

    @Test
    void 분포_배열은_오름차순으로_정렬된다() {
        JobSpecProfile profile = builder.build("BACKEND", List.of(
                passer("4.20", "4.50", 900, new String[]{"정보처리기사", "SQLD"}, 2),
                passer("3.50", "4.50", 700, new String[]{}, 0),
                passer("3.90", "4.50", 850, new String[]{"SQLD"}, 1)));

        assertThat(profile.getGpaRatios()).isSorted();
        assertThat(profile.getToeicEquivalents()).containsExactly(700.0, 850.0, 900.0);
        assertThat(profile.getCertCounts()).containsExactly(0, 1, 2);
        assertThat(profile.getExperienceCounts()).containsExactly(0, 1, 2);
        assertThat(profile.getSampleSize()).isEqualTo(3);
    }

    @Test
    void 결측_합격자는_해당_축_분포에서만_빠진다() {
        // 학점 없는 합격자는 gpaRatios에서만 빠지고 sampleSize·certCounts에는 남는다.
        // 결측을 0으로 끼워 넣으면 분포가 실제보다 아래로 왜곡된다(v7 "결측은 중립" 원칙).
        PasserData noGpa = PasserData.builder()
                .languageScores(List.of(Map.of("type", "TOEIC", "score", 800)))
                .certifications(new String[]{"SQLD"})
                .experienceCount(1)
                .build();

        JobSpecProfile profile = builder.build("BACKEND", List.of(
                passer("4.00", "4.50", 900, new String[]{"정보처리기사"}, 2), noGpa));

        assertThat(profile.getGpaRatios()).hasSize(1);
        assertThat(profile.getToeicEquivalents()).hasSize(2);
        assertThat(profile.getSampleSize()).isEqualTo(2);
        assertThat(profile.getCertCounts()).containsExactly(1, 1);
    }

    @Test
    void 자격증_보유율의_분모는_자격증_없는_합격자를_포함한_전원이다() {
        // 4명 중 1명 보유 = 25%. 보유자만 분모로 쓰면 100%로 부풀려진다.
        JobSpecProfile profile = builder.build("BACKEND", List.of(
                passer("3.80", "4.50", 800, new String[]{"SQLD"}, 1),
                passer("3.80", "4.50", 800, new String[]{}, 1),
                passer("3.80", "4.50", 800, null, 1),
                passer("3.80", "4.50", 800, new String[]{}, 1)));

        CertStat sqld = profile.getCertStats().get(0);
        assertThat(sqld.getCanonicalName()).isEqualTo("SQLD");
        assertThat(sqld.getHolders()).isEqualTo(1);
        assertThat(sqld.getHolderRate()).isEqualTo(0.25);
    }

    @Test
    void 같은_자격증의_표기_변형은_한_사람당_한_번만_센다() {
        // "SQLD 필기"와 "SQL개발자"는 정규화하면 전부 SQLD — 1명이 3번 적어도 보유자 1명.
        JobSpecProfile profile = builder.build("BACKEND", List.of(
                passer("3.80", "4.50", 800, new String[]{"SQLD", "SQLD 필기", "SQL개발자"}, 1),
                passer("3.80", "4.50", 800, new String[]{}, 1)));

        assertThat(profile.getCertStats()).hasSize(1);
        assertThat(profile.getCertStats().get(0).getHolders()).isEqualTo(1);
        assertThat(profile.getCertCounts()).containsExactly(0, 1);
    }

    @Test
    void 자격증_통계는_보유율_내림차순으로_정렬된다() {
        JobSpecProfile profile = builder.build("BACKEND", List.of(
                passer("3.80", "4.50", 800, new String[]{"정보처리기사", "SQLD"}, 1),
                passer("3.80", "4.50", 800, new String[]{"정보처리기사"}, 1),
                passer("3.80", "4.50", 800, new String[]{"ADsP"}, 1)));

        assertThat(profile.getCertStats()).extracting(CertStat::getCanonicalName)
                .containsExactly("정보처리기사", "ADSP", "SQLD"); // 2/3 > 1/3(동률은 이름순)
    }

    @Test
    void 화면_표기는_합격자들이_가장_많이_쓴_원본_표기를_쓴다() {
        JobSpecProfile profile = builder.build("BACKEND", List.of(
                passer("3.80", "4.50", 800, new String[]{"ADsP"}, 1),
                passer("3.80", "4.50", 800, new String[]{"ADsP"}, 1),
                passer("3.80", "4.50", 800, new String[]{"데이터분석준전문가"}, 1)));

        CertStat adsp = profile.getCertStats().get(0);
        assertThat(adsp.getCanonicalName()).isEqualTo("ADSP");
        assertThat(adsp.getDisplayName()).isEqualTo("ADsP"); // 2회 > 1회
        assertThat(adsp.getHolders()).isEqualTo(3);
    }

    @Test
    void 빈_목록이면_빈_프로필을_만든다() {
        JobSpecProfile profile = builder.build("BACKEND", List.of());

        assertThat(profile.getSampleSize()).isZero();
        assertThat(profile.getGpaRatios()).isEmpty();
        assertThat(profile.getCertStats()).isEmpty();
    }

    @Test
    void null_원소는_무시한다() {
        JobSpecProfile profile = builder.build("BACKEND", Arrays.asList(
                passer("3.80", "4.50", 800, new String[]{"SQLD"}, 1), null));

        assertThat(profile.getSampleSize()).isEqualTo(1);
    }

    private PasserData passer(String gpa, String gpaMax, int toeic, String[] certs, int expCount) {
        return PasserData.builder()
                .gpa(new BigDecimal(gpa))
                .gpaMax(new BigDecimal(gpaMax))
                .languageScores(List.of(Map.of("type", "TOEIC", "score", toeic)))
                .certifications(certs)
                .experienceCount(expCount)
                .build();
    }
}
