package com.career.recommendation.util;

import com.career.recommendation.dto.position.SpecPositionResult;
import com.career.recommendation.entity.Activity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GapMatcherTest {

    @Test
    void 알려진_갭은_자격증_갭_뒤에_미입력이거나_하위인_어학_경험_축이_붙는다() {
        SpecPositionResult position = SpecPositionResult.builder()
                .basis("JOB")
                .gaps(List.of(gap("정보처리기사", 70), gap("SQLD", 55)))
                .axes(List.of(
                        axis("학점", 72),          // 상위 — 갭 아님
                        axis("어학 성적", 30),      // 하위 — 갭
                        axis("자격증", 40),         // 자격증 축은 개별 갭으로 이미 표현됨
                        axis("경험", null)))        // 미입력 — 갭
                .build();

        assertThat(GapMatcher.knownGapNames(position))
                .containsExactly("정보처리기사", "SQLD", "어학 성적", "경험");
    }

    @Test
    void Gemini가_준_targetGap은_알려진_이름에_있을_때만_받고_표기_차이는_정규화로_흡수한다() {
        List<String> known = List.of("정보처리기사", "어학 성적");

        assertThat(GapMatcher.normalizeTargetGap("정보처리기사", known)).contains("정보처리기사");
        assertThat(GapMatcher.normalizeTargetGap(" 정보 처리 기사 ", known)).contains("정보처리기사");
        assertThat(GapMatcher.normalizeTargetGap("어학 성적", known)).contains("어학 성적");
        // 비교 탭에 없는 갭을 지어내면 버린다 — 두 화면이 다른 말을 하지 않게
        assertThat(GapMatcher.normalizeTargetGap("커뮤니케이션 역량", known)).isEmpty();
        assertThat(GapMatcher.normalizeTargetGap(null, known)).isEmpty();
    }

    @Test
    void 활동_본문에_갭_키워드가_있으면_그_갭으로_매칭한다() {
        List<String> known = List.of("정보처리기사", "어학 성적", "경험");

        assertThat(GapMatcher.matchGap(activity("정보처리기사 실기 대비반", null, null), known)).contains("정보처리기사");
        assertThat(GapMatcher.matchGap(activity("TOEIC 집중 스터디", null, null), known)).contains("어학 성적");
        assertThat(GapMatcher.matchGap(activity("여름 인턴십", "백엔드 인턴 모집", null), known)).contains("경험");
        assertThat(GapMatcher.matchGap(activity("디자인 공모전", null, new String[]{"디자인"}), known)).contains("경험");
        assertThat(GapMatcher.matchGap(activity("교양 특강", "철학 강연", null), known)).isEmpty();
    }

    @Test
    void 폴백_순위는_갭_매칭이_직무_일치보다_앞서고_같은_점수면_마감_임박순이다() {
        List<String> known = List.of("SQLD");
        Activity jobOnly = activity("백엔드 부트캠프", null, new String[]{"백엔드"});           // 직무 +2, 경험 키워드는 known에 없음
        Activity gapOnly = activity("SQLD 자격증 특강", null, new String[]{"데이터"});            // 갭 +3
        Activity both = activity("백엔드 SQLD 스터디", null, new String[]{"백엔드"});             // +5
        Activity none1 = activity("교양 특강", null, null);
        none1.setDeadline(LocalDate.of(2026, 9, 1));
        Activity none2 = activity("독서 모임", null, null);
        none2.setDeadline(LocalDate.of(2026, 8, 25));

        List<GapMatcher.Ranked> ranked = GapMatcher.rankForFallback(
                List.of(none1, jobOnly, none2, gapOnly, both), "BACKEND", known, 5);

        assertThat(ranked).extracting(r -> r.activity().getName())
                .containsExactly("백엔드 SQLD 스터디", "SQLD 자격증 특강", "백엔드 부트캠프", "독서 모임", "교양 특강");
        assertThat(ranked.get(0).targetGap()).isEqualTo("SQLD");
        assertThat(ranked.get(2).targetGap()).isNull();
    }

    @Test
    void 폴백_순위는_limit만큼만_돌려주고_중복_id는_제거한다() {
        Activity a = activity("A", null, null);
        List<GapMatcher.Ranked> ranked = GapMatcher.rankForFallback(List.of(a, a, activity("B", null, null)), null, List.of(), 1);
        assertThat(ranked).hasSize(1);
    }

    private SpecPositionResult.SpecGap gap(String name, int rate) {
        return SpecPositionResult.SpecGap.builder().name(name).holderRatePercent(rate).build();
    }

    private SpecPositionResult.AxisPosition axis(String label, Integer percentile) {
        return SpecPositionResult.AxisPosition.builder().axis(label).label(label).percentile(percentile).build();
    }

    private Activity activity(String name, String description, String[] tags) {
        return Activity.builder().id(UUID.randomUUID()).type("EDUCATION").name(name)
                .description(description).tags(tags).build();
    }
}
