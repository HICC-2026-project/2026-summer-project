package com.career.recommendation.util;

import com.career.recommendation.dto.recommendation.RecommendationResponse;
import com.career.recommendation.dto.recommendation.RecommendationResponse.ActivityRecommendation;

import java.time.LocalDate;
import java.util.List;

/**
 * BE-1 담당 — Claude API 완전 실패(2회 연속 JSON 파싱 오류) 시 반환할 기본 추천 활동 목록.
 * 에러 화면 노출 금지 원칙에 따라 항상 안전한 응답을 보장한다.
 * isAiRecommendation = false 로 내려가므로 프론트에서 "일반 추천" 배지를 표시한다.
 *
 * ※ 최종 목록은 BE-1·BE-2 브레인스토밍 결과를 반영해 확정 예정 (현재 초안).
 */
public final class RecommendationFallbackData {

    private RecommendationFallbackData() {}

    public static RecommendationResponse get() {
        return RecommendationResponse.builder()
                .activities(FALLBACK_ACTIVITIES)
                .matchScore(0)
                .comparisonMessage("아직 이 활동은 데이터가 부족해 AI 일반 추천을 제공합니다.")
                .isAiRecommendation(false)
                .build();
    }

    private static final List<ActivityRecommendation> FALLBACK_ACTIVITIES = List.of(
            ActivityRecommendation.builder()
                    .id(null)
                    .type("교육")
                    .name("SSAFY (삼성 청년 SW 아카데미)")
                    .reason("SW 역량 강화와 취업 연계 프로그램으로 높은 취업률을 자랑합니다.")
                    .deadline(null)
                    .build(),
            ActivityRecommendation.builder()
                    .id(null)
                    .type("교육")
                    .name("우아한테크코스")
                    .reason("실무 중심 프로젝트 경험과 코드 리뷰 문화를 익힐 수 있습니다.")
                    .deadline(null)
                    .build(),
            ActivityRecommendation.builder()
                    .id(null)
                    .type("교육")
                    .name("네이버 부스트캠프")
                    .reason("AI·웹 트랙을 통해 대기업 수준의 개발 역량을 쌓을 수 있습니다.")
                    .deadline(null)
                    .build(),
            ActivityRecommendation.builder()
                    .id(null)
                    .type("교육")
                    .name("42서울")
                    .reason("동료 학습 기반으로 자기주도 역량과 문제 해결 능력을 키울 수 있습니다.")
                    .deadline(null)
                    .build(),
            ActivityRecommendation.builder()
                    .id(null)
                    .type("인턴십")
                    .name("현대 소프티어 부트캠프")
                    .reason("현대자동차그룹 계열 SW 인재 육성 프로그램으로 채용 연계율이 높습니다.")
                    .deadline(null)
                    .build()
    );
}
