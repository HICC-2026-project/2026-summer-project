package com.career.recommendation.service;

import com.career.recommendation.domain.JobType;
import com.career.recommendation.dto.activity.ActivityResponse;
import com.career.recommendation.entity.Activity;
import com.career.recommendation.exception.ActivityNotFoundException;
import com.career.recommendation.repository.ActivityRepository;
import com.career.recommendation.util.GapMatcher;
import com.career.recommendation.util.ServiceTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private final ActivityRepository activityRepository;

    /** deadlineAfter "조건 없음" 센티널 — JPQL의 null 파라미터는 Postgres가 타입을 못 정해 실패한다. */
    private static final LocalDate NO_DEADLINE_FILTER = LocalDate.of(1970, 1, 1);

    /**
     * 활동 목록 검색 조건. 모두 선택이며 null/빈값은 "조건 없음".
     *
     * @param type          활동 유형 코드(INTERNSHIP·EXTERNAL·COMPETITION·EDUCATION)
     * @param jobType       목표 직무 — 태그가 그 직무 키워드(GapMatcher.jobKeywords)에 맞는 활동만
     * @param deadlineAfter 이 날짜 이후 마감(상시 포함)만
     * @param keyword       이름·주최·설명 부분 일치(대소문자 무시)
     */
    public record ActivityFilter(String type, JobType jobType, LocalDate deadlineAfter, String keyword) {
        public static ActivityFilter none() {
            return new ActivityFilter(null, null, null, null);
        }
    }

    public Page<ActivityResponse> getActivities(ActivityFilter filter, Pageable pageable) {
        LocalDate today = LocalDate.now(ServiceTime.ZONE_ID);
        String type = filter.type() == null ? "" : filter.type().trim();
        String keywordLike = filter.keyword() == null || filter.keyword().isBlank()
                ? ""
                : "%" + escapeLike(filter.keyword().trim().toLowerCase(Locale.ROOT)) + "%";
        String jobPattern = filter.jobType() == null
                ? ""
                : GapMatcher.jobKeywords(filter.jobType()).stream()
                        .map(kw -> {
                            String esc = escapePosixRegex(kw);
                            // 짧은 영문 약어(ai·ml 등)는 \y로 감싸 "html"의 "ml" 같은 태그 내 부분문자열 오매칭을 막는다.
                            return GapMatcher.needsWordBoundary(kw) ? "\\y" + esc + "\\y" : esc;
                        })
                        .collect(Collectors.joining("|"));

        return activityRepository
                .searchOpenActivities(today, type,
                        filter.deadlineAfter() != null ? filter.deadlineAfter() : NO_DEADLINE_FILTER,
                        keywordLike, jobPattern, pageable)
                // 목록 응답은 description을 200자로 줄인다(ActivityResponse.fromSummary) — 상세
                // 조회(getActivity)는 from()으로 전문을 그대로 유지한다.
                .map(ActivityResponse::fromSummary);
    }

    public ActivityResponse getActivity(UUID activityId) {
        Activity activity = activityRepository.findById(activityId)
                .filter(foundActivity -> Boolean.TRUE.equals(foundActivity.getIsActive()))
                .orElseThrow(() -> new ActivityNotFoundException(activityId));

        return ActivityResponse.from(activity);
    }

    /** PostgreSQL POSIX 정규식 메타문자 이스케이프 — Java Pattern.quote의 \Q…\E는 Postgres가 모른다. */
    private static String escapePosixRegex(String raw) {
        return raw.replaceAll("([\\\\.^$|()\\[\\]{}*+?])", "\\\\$1");
    }

    /** LIKE 메타문자(%·_)를 리터럴로 — 사용자가 "%"를 넣어 전체를 긁는 것을 막는다. 이스케이프 문자는 쿼리의 ESCAPE '!'와 맞춘다. */
    private static String escapeLike(String raw) {
        return raw.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
