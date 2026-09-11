package com.career.recommendation.exception;

import java.util.UUID;

public class PasserReportNotFoundException extends RuntimeException {

    public PasserReportNotFoundException(UUID reportId) {
        super("제보를 찾을 수 없습니다. id=" + reportId);
    }
}
