import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { CompareTab } from "./CompareTab";
import type { RecommendationMeta, SpecPosition } from "../../types";

// 비교 탭의 렌더 규칙을 고정한다 — 백엔드 v9 약속(미입력 ≠ 0점, 폴백 사실 고지, 표본 부족 시 제보 유도)이
// 화면에서 어떻게 보여야 하는지가 여기 적혀 있다.

function meta(position: SpecPosition): RecommendationMeta {
  return { specPosition: position, isAiRecommendation: true, dailyLimitReached: false };
}

const base: SpecPosition = {
  basis: "JOB",
  basisMessage: "백엔드 합격자 12명의 분포와 비교한 결과입니다.",
  sampleSize: 12,
  targetJobType: "BACKEND",
  targetJobLabel: "백엔드",
  jobSampleSize: 12,
  minSampleSize: 3,
  axes: [
    { axis: "GPA", label: "학점", myValue: "3.80/4.5", medianValue: "3.60/4.5", percentile: 72, coverage: 12 },
    { axis: "LANGUAGE", label: "어학 성적", myValue: "미입력", medianValue: "환산 850", percentile: null, coverage: 10 },
  ],
  gaps: [{ name: "정보처리기사", holderRatePercent: 70 }],
  matchedCertifications: [],
  unmatchedCertifications: [],
};

describe("CompareTab", () => {
  it("percentile이 있는 축은 막대를 그리고, 미입력(null) 축은 막대 없이 '미입력'만 표시한다", () => {
    const { container } = render(<CompareTab isDemo={false} recMeta={meta(base)} />);

    expect(screen.getByText("상위 28%")).toBeInTheDocument();
    // "미입력"은 내 값 칸과 percentile 배지 두 곳에 뜬다 — 둘 다 미입력이어야 한다(0점·최하위로 그리면 안 됨)
    expect(screen.getAllByText("미입력")).toHaveLength(2);
    expect(screen.queryByText("최하위")).not.toBeInTheDocument();
    // 내 막대(width: NN%)는 percentile이 있는 축에만 1개. 중앙값 막대(50%)는 축마다 1개.
    const myBars = container.querySelectorAll<HTMLElement>('div[style*="width: 72%"]');
    expect(myBars).toHaveLength(1);
    expect(container.querySelectorAll<HTMLElement>('div[style*="width: 50%"]')).toHaveLength(2);
  });

  it("갭은 보유율과 함께 보이고, 직무 표본이 충분하면 제보 유도 카드가 없다", () => {
    render(<CompareTab isDemo={false} recMeta={meta(base)} onOpenPasserReport={() => {}} />);

    expect(screen.getByText("정보처리기사")).toBeInTheDocument();
    expect(screen.getByText("합격자 70% 보유")).toBeInTheDocument();
    expect(screen.queryByText("합격자 스펙 제보하기")).not.toBeInTheDocument();
  });

  it("전체 폴백(OVERALL)이면 백엔드 문구를 그대로 보여주고 직무 표본 수와 함께 제보를 유도한다", () => {
    const onOpen = vi.fn();
    render(
      <CompareTab
        isDemo={false}
        recMeta={meta({
          ...base,
          basis: "OVERALL",
          basisMessage: "백엔드 합격자 데이터가 부족해, 직무 구분 없이 전체 합격자 30명의 분포와 비교한 결과입니다.",
          sampleSize: 30,
          jobSampleSize: 1,
        })}
        onOpenPasserReport={onOpen}
      />,
    );

    expect(screen.getByText(/직무 구분 없이 전체 합격자 30명/)).toBeInTheDocument();
    expect(screen.getByText("백엔드 합격자 데이터가 1명뿐이에요")).toBeInTheDocument();
    fireEvent.click(screen.getByText("합격자 스펙 제보하기"));
    expect(onOpen).toHaveBeenCalledTimes(1);
  });

  it("데이터 부족(NONE)이면 축·갭 없이 안내와 제보 유도만 보이고, 미매칭 자격증 고지는 살아 있다", () => {
    render(
      <CompareTab
        isDemo={false}
        recMeta={meta({ ...base, basis: "NONE", sampleSize: 0, jobSampleSize: 0, axes: [], gaps: [], unmatchedCertifications: ["정크자격증"] })}
        onOpenPasserReport={() => {}}
      />,
    );

    expect(screen.getByText("비교 가능한 데이터가 부족해요")).toBeInTheDocument();
    expect(screen.getByText("합격자 스펙 제보하기")).toBeInTheDocument();
    expect(screen.getByText(/정크자격증/)).toBeInTheDocument();
    expect(screen.queryByText("학점")).not.toBeInTheDocument();
  });

  it("예시 화면(isDemo)에서는 제보 유도를 띄우지 않는다", () => {
    render(<CompareTab isDemo recMeta={null} onOpenPasserReport={() => {}} />);
    expect(screen.queryByText("합격자 스펙 제보하기")).not.toBeInTheDocument();
  });

  it("areaCoverage가 있으면 보유/미보유 칩을 나누어 보여주고, 미보유가 있으면 안내 문구를 띄운다", () => {
    render(
      <CompareTab
        isDemo={false}
        recMeta={meta({
          ...base,
          areaCoverage: [
            { area: "API", label: "API 개발", covered: true },
            { area: "AUTH", label: "인증", covered: false },
          ],
        })}
      />,
    );

    expect(screen.getByText("영역 커버리지")).toBeInTheDocument();
    expect(screen.getByText("✓ API 개발")).toBeInTheDocument();
    expect(screen.getByText("인증")).toBeInTheDocument();
    expect(screen.getByText("비어 있는 영역은 추천 활동으로 채워보세요")).toBeInTheDocument();
  });

  it("areaCoverage가 undefined/null/빈 배열이면 영역 커버리지 섹션 자체를 렌더하지 않는다", () => {
    const { rerender } = render(<CompareTab isDemo={false} recMeta={meta(base)} />);
    expect(screen.queryByText("영역 커버리지")).not.toBeInTheDocument();

    rerender(<CompareTab isDemo={false} recMeta={meta({ ...base, areaCoverage: null })} />);
    expect(screen.queryByText("영역 커버리지")).not.toBeInTheDocument();

    rerender(<CompareTab isDemo={false} recMeta={meta({ ...base, areaCoverage: [] })} />);
    expect(screen.queryByText("영역 커버리지")).not.toBeInTheDocument();
  });
});
