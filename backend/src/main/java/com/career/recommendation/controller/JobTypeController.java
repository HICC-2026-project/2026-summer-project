package com.career.recommendation.controller;

import com.career.recommendation.domain.JobType;
import com.career.recommendation.dto.job.JobTypeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 프론트가 직무 목록(JOB_OPTIONS)을 하드코딩하지 않도록 서버의 JobType을 그대로 내려준다.
 * 인증 불필요 — 온보딩 전 화면에서도 쓰인다.
 */
@Tag(name = "Job", description = "직무 코드 목록")
@RestController
@RequestMapping("/api/v1/jobs")
public class JobTypeController {

    @Operation(summary = "지원 직무 목록", description = "목표 직무·합격자 제보에 쓸 수 있는 직무 코드와 한글 라벨. 순서는 화면 표시 순서.")
    @GetMapping
    public List<JobTypeResponse> list() {
        return Arrays.stream(JobType.values()).map(JobTypeResponse::from).toList();
    }
}
