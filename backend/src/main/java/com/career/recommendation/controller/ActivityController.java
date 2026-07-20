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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "활동", description = "인턴십·대외활동·공모전 목록 조회 (로그인 불필요)")
@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @Operation(summary = "활동 목록 조회", description = "활동 목록을 페이지네이션으로 조회한다. type으로 필터링 가능.")
    @GetMapping
    public Page<ActivityResponse> getActivities(
            @Parameter(description = "활동 유형 필터 (예: 인턴십·대외활동·공모전, 생략 시 전체)")
            @RequestParam(required = false) String type,
            @Parameter(description = "페이지 번호 (0부터 시작)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "정렬 기준 필드")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "정렬 방향 (ASC/DESC)")
            @RequestParam(defaultValue = "DESC") Sort.Direction direction
    ) {
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(direction, sortBy)
        );

        return activityService.getActivities(type, pageRequest);
    }
}