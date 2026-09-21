import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { HomeTab } from "./HomeTab";
import type { Recommendation, RecommendationMeta, Spec, Target } from "../../types";

// E10-2(F-09) 추천 피드백 버튼(👍/👎)의 핵심 규칙만 고정한다: 렌더+활성 표시, 클릭 시 API 호출.
// api.ts는 실제 네트워크를 타지 않도록 모킹한다(GithubSection 등과 달리 이 컴포넌트는
// 컨테이너 없이 api 모듈을 직접 호출한다 — HomeTab.tsx의 handleReaction 참고).

const { postRecommendationFeedback, deleteRecommendationFeedback } = vi.hoisted(() => ({
  postRecommendationFeedback: vi.fn().mockResolvedValue(undefined),
  deleteRecommendationFeedback: vi.fn().mockResolvedValue(undefined),
}));

vi.mock("../../api", () => ({
  postRecommendationFeedback,
  deleteRecommendationFeedback,
}));

const spec: Spec = { gpa: "3.8", gpaScale: 4.5, grade: 3, langScores: {}, certs: [], experiences: [] };
const target: Target = { job: "BACKEND", size: "대기업", industry: "IT" };
const meta: RecommendationMeta = { specPosition: null, isAiRecommendation: true, dailyLimitReached: false };

function recommendation(overrides: Partial<Recommendation> = {}): Recommendation {
  return {
    id: "activity-1",
    type: "INTERNSHIP",
    name: "백엔드 인턴",
    reason: "추천 이유",
    deadline: "2026-12-31",
    ...overrides,
  };
}

describe("HomeTab 추천 피드백 버튼", () => {
  it("👍/👎 버튼을 렌더하고, 서버가 준 myReaction을 활성 상태로 보여준다", () => {
    render(
      <HomeTab
        spec={spec}
        target={target}
        nickname="테스터"
        isDemo={false}
        recommendations={[recommendation({ myReaction: "LIKE" })]}
        recMeta={meta}
        recLoading={false}
        recError={false}
        onOpenDetail={() => {}}
      />,
    );

    expect(screen.getByLabelText("이 활동이 마음에 들어요")).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByLabelText("이 활동은 관심 없어요")).toHaveAttribute("aria-pressed", "false");
  });

  it("클릭하면 반응 API를 호출하고 '다음 추천 갱신 때 반영돼요' 안내를 보여준다", async () => {
    render(
      <HomeTab
        spec={spec}
        target={target}
        nickname="테스터"
        isDemo={false}
        recommendations={[recommendation()]}
        recMeta={meta}
        recLoading={false}
        recError={false}
        onOpenDetail={() => {}}
      />,
    );

    fireEvent.click(screen.getByLabelText("이 활동은 관심 없어요"));

    await waitFor(() => expect(postRecommendationFeedback).toHaveBeenCalledWith("activity-1", "DISLIKE"));
    expect(deleteRecommendationFeedback).not.toHaveBeenCalled();
    expect(await screen.findByText("다음 추천 갱신 때 반영돼요")).toBeInTheDocument();
  });
});
