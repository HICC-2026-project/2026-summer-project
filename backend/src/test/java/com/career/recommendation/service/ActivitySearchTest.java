package com.career.recommendation.service;

import com.career.recommendation.domain.JobType;
import com.career.recommendation.dto.activity.ActivityResponse;
import com.career.recommendation.entity.Activity;
import com.career.recommendation.repository.ActivityRepository;
import com.career.recommendation.service.ActivityService.ActivityFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 활동 검색 JPQL(texticregexeq·array_to_string 함수 호출, LIKE 이스케이프)이 실제 PostgreSQL에서
 * 의도대로 도는지 본다. 시드 데이터와 섞이지 않게 이 테스트만의 접두사를 키워드로 쓴다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ActivitySearchTest {

    private static final String MARK = "zzsearch" + UUID.randomUUID().toString().substring(0, 6);

    @Autowired private ActivityService activityService;
    @Autowired private ActivityRepository activityRepository;

    private final LocalDate far = LocalDate.now().plusYears(1);

    @BeforeEach
    void seed() {
        save("백엔드 부트캠프 " + MARK, "EDUCATION", "네이버", far, "백엔드", "클라우드");
        save("프론트 해커톤 " + MARK, "COMPETITION", "카카오", far.plusMonths(2), "프론트엔드");
        save("데이터 인턴 " + MARK, "INTERNSHIP", "토스", null, "데이터");
        save("마감 지난 백엔드 " + MARK, "EDUCATION", "삼성", LocalDate.now().minusDays(1), "백엔드");
        save("100% 특가 " + MARK, "EDUCATION", "기타", far);
        save("HTML 기초반 " + MARK, "EDUCATION", "위코드", far, "HTML");
        save("AI 부트캠프 " + MARK, "EDUCATION", "업스테이지", far, "AI");
    }

    @Test
    void 필터_없이_오늘_이후_마감이거나_상시인_활동만_나온다() {
        List<String> names = names(ActivityFilter.none());
        assertThat(names).contains("백엔드 부트캠프 " + MARK, "프론트 해커톤 " + MARK, "데이터 인턴 " + MARK);
        assertThat(names).doesNotContain("마감 지난 백엔드 " + MARK);
    }

    @Test
    void jobType은_태그를_직무_키워드_정규식으로_거른다() {
        assertThat(names(new ActivityFilter(null, JobType.BACKEND, null, MARK)))
                .containsExactly("백엔드 부트캠프 " + MARK);
        assertThat(names(new ActivityFilter(null, JobType.FRONTEND, null, MARK)))
                .containsExactly("프론트 해커톤 " + MARK);
        assertThat(names(new ActivityFilter(null, JobType.SECURITY, null, MARK))).isEmpty();
    }

    @Test
    void jobType_AI_ML은_태그의_긴_영문_단어_속_부분문자열이_아니라_독립된_토큰만_잡는다() {
        // "HTML" 태그에 "ml"이 부분문자열로 들어있지만 단어 경계가 아니므로 매칭되면 안 된다
        assertThat(names(new ActivityFilter(null, JobType.AI_ML, null, MARK)))
                .containsExactly("AI 부트캠프 " + MARK);
    }

    @Test
    void deadlineAfter는_그_날짜_이후_마감만_남기되_상시_모집은_항상_포함한다() {
        assertThat(names(new ActivityFilter(null, null, far.plusMonths(1), MARK)))
                .containsExactlyInAnyOrder("프론트 해커톤 " + MARK, "데이터 인턴 " + MARK);
    }

    @Test
    void keyword는_이름_주최_설명에서_대소문자_무시로_찾고_LIKE_메타문자는_리터럴이다() {
        assertThat(names(new ActivityFilter(null, null, null, "토스"))).contains("데이터 인턴 " + MARK);
        assertThat(names(new ActivityFilter(null, null, null, MARK.toUpperCase()))).hasSize(4);
        // "%"를 넣어도 와일드카드가 아니라 글자 그대로 — 전체를 긁을 수 없다
        assertThat(names(new ActivityFilter(null, null, null, "100% 특가"))).containsExactly("100% 특가 " + MARK);
        assertThat(names(new ActivityFilter(null, null, null, "%" + MARK))).isEmpty();
    }

    @Test
    void 필터는_AND로_결합된다() {
        assertThat(names(new ActivityFilter("EDUCATION", JobType.BACKEND, null, MARK)))
                .containsExactly("백엔드 부트캠프 " + MARK);
        assertThat(names(new ActivityFilter("INTERNSHIP", JobType.BACKEND, null, MARK))).isEmpty();
    }

    private List<String> names(ActivityFilter filter) {
        Page<ActivityResponse> page = activityService.getActivities(filter, PageRequest.of(0, 100, Sort.by("name")));
        return page.getContent().stream().map(ActivityResponse::getName).filter(n -> n.contains(MARK)).toList();
    }

    private void save(String name, String type, String org, LocalDate deadline, String... tags) {
        activityRepository.saveAndFlush(Activity.builder()
                .type(type).name(name).organization(org).deadline(deadline)
                .tags(tags.length == 0 ? null : tags).isActive(true).build());
    }
}
