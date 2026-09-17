import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ExperienceEnrichModalView } from "./ExperienceEnrichModal";

// AI 심층 질문 모달의 순수 표시 분기(질문 표시 / 결과 미리보기)를 고정한다.
// API 호출·단계 전이는 컨테이너(ExperienceEnrichModal)가 맡고, 여기서는 GithubSectionView.test.tsx와
// 같은 패턴으로 phase를 직접 주입해 뷰만 검증한다.

function noop() {}

describe("ExperienceEnrichModalView", () => {
  it("질문 단계에서는 질문·답변 textarea를 보여주고, 답변이 하나도 없으면 분석하기를 막는다", () => {
    render(
      <ExperienceEnrichModalView
        phase={{ kind: "questions", questions: ["어떤 역할을 맡았나요?", "가장 어려웠던 점은?"] }}
        answers={["", ""]}
        onAnswerChange={noop}
        onSubmit={noop}
        onApply={noop}
        onClose={noop}
      />,
    );

    expect(screen.getByText("어떤 역할을 맡았나요?")).toBeInTheDocument();
    expect(screen.getByText("가장 어려웠던 점은?")).toBeInTheDocument();
    expect(screen.getByText("분석하기")).toBeDisabled();
    expect(screen.getByText("건너뛰기")).toBeInTheDocument();
  });

  it("답변이 하나라도 있으면 분석하기가 활성화되고 클릭 시 onSubmit이 호출된다", () => {
    const onSubmit = vi.fn();
    render(
      <ExperienceEnrichModalView
        phase={{ kind: "questions", questions: ["어떤 역할을 맡았나요?"] }}
        answers={["백엔드 API를 설계했어요"]}
        onAnswerChange={noop}
        onSubmit={onSubmit}
        onApply={noop}
        onClose={noop}
      />,
    );

    const button = screen.getByText("분석하기");
    expect(button).not.toBeDisabled();
    fireEvent.click(button);
    expect(onSubmit).toHaveBeenCalledTimes(1);
  });

  it("결과 미리보기에서는 영역 칩·깊이 배지·역할 요약을 보여주고, 적용/취소 버튼이 각 콜백을 호출한다", () => {
    const onApply = vi.fn();
    const onClose = vi.fn();
    render(
      <ExperienceEnrichModalView
        phase={{
          kind: "result",
          result: { areas: ["API", "DB"], depth: "IMPLEMENTED", roleSummary: "백엔드 API 설계·구현을 주도했어요" },
        }}
        answers={[]}
        onAnswerChange={noop}
        onSubmit={noop}
        onApply={onApply}
        onClose={onClose}
      />,
    );

    expect(screen.getByText("API 개발")).toBeInTheDocument();
    expect(screen.getByText("데이터베이스")).toBeInTheDocument();
    expect(screen.getByText("직접 구현")).toBeInTheDocument();
    expect(screen.getByText("백엔드 API 설계·구현을 주도했어요")).toBeInTheDocument();

    fireEvent.click(screen.getByText("적용"));
    expect(onApply).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByText("취소"));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("질문을 만들 수 없거나(no-questions) 분석 실패(enrich-failed)면 각각의 안내 문구를 보여준다", () => {
    const { rerender } = render(
      <ExperienceEnrichModalView phase={{ kind: "no-questions" }} answers={[]} onAnswerChange={noop} onSubmit={noop} onApply={noop} onClose={noop} />,
    );
    expect(screen.getByText("지금은 질문을 만들 수 없어요. 잠시 후 다시 시도해 주세요.")).toBeInTheDocument();

    rerender(
      <ExperienceEnrichModalView phase={{ kind: "enrich-failed" }} answers={[]} onAnswerChange={noop} onSubmit={noop} onApply={noop} onClose={noop} />,
    );
    expect(screen.getByText("분석에 실패했어요.")).toBeInTheDocument();
  });
});
