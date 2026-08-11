package com.career.recommendation.controller;

import com.career.recommendation.dto.activity.ActivityResponse;
import com.career.recommendation.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Set;
import java.util.UUID;

@Tag(name = "활동", description = "인턴십·대외활동·공모전·교육  목록 조회 (로그인 불필요)")
@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /**
     * 정렬 가능한 필드 화이트리스트. Activity 엔티티 필드명을 그대로 Sort.by()에 넘기면
     * 존재하지 않는 필드가 PropertyReferenceException으로 터지고(그 메시지엔 엔티티의
     * 실제 프로퍼티 목록이 담긴다), 이 엔드포인트는 인증 없이 호출 가능해(PUBLIC_URLS)
     * 도메인 모델을 정찰하는 데 악용될 수 있다. 화이트리스트로 사전에 막는다.
     */
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("createdAt", "deadline", "name", "startDate", "endDate");

    /** 미인증으로 호출 가능한 엔드포인트라 비정상적으로 큰 size로 DB 전체를 끌어오는 걸 막는다. */
    private static final int MAX_PAGE_SIZE = 100;

    @Operation(summary = "활동 목록 조회", description = "활동 목록을 페이지네이션으로 조회한다. type으로 필터링 가능.")
    @GetMapping
    public Page<ActivityResponse> getActivities(
            @Parameter(description = "활동 유형 필터 (예: 인턴십·대외활동·공모전·교육, 생략 시 전체)")
            @RequestParam(required = false) String type,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (최대 " + MAX_PAGE_SIZE + ")")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "정렬 기준 필드")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "정렬 방향 (ASC/DESC, 대소문자 무관)")
            @RequestParam(defaultValue = "DESC") String direction
    ) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "size는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "sortBy는 " + SORTABLE_FIELDS + " 중 하나여야 합니다.");
        }

        // direction을 Sort.Direction으로 직접 바인딩하면(Enum.valueOf 기반) 대소문자를
        // 가려 "desc"·"asc" 같은 흔한 소문자 입력이 400이 났다. 이 엔드포인트는 인증이
        // 필요 없어(PUBLIC_URLS) 다양한 클라이언트가 호출할 수 있다. fromString()은
        // 대소문자를 가리지 않고, 그 외 값은 IllegalArgumentException → 전용 핸들러로
        // 정확히 400이 나가 기존 검증들과 일관된 실패 방식을 유지한다.
        Sort.Direction sortDirection = Sort.Direction.fromString(direction);

        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(sortDirection, sortBy)
        );

        return activityService.getActivities(type, pageRequest);
    }

    @Operation(
            summary = "활동 상세 조회",
            description = "활동 ID를 사용하여 활동 상세 정보를 조회합니다."
    )
    @GetMapping("/{id}")
    public ActivityResponse getActivity(
            @Parameter(description = "조회할 활동 ID")
            @PathVariable UUID id
    ) {
        return activityService.getActivity(id);
    }
}
