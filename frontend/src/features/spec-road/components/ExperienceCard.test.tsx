import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ExperienceCard } from "./ExperienceCard";
import type { Experience } from "../types";

// E11 1단계로 추가된 선택 필드(months·role·stack·areas·depth)가 경험 카드에
// 함께 렌더되는지, 없을 때는 유형·제목만 남는지를 고정한다.
describe("ExperienceCard", () => {
  it("months·role·stack·areas·depth가 모두 있으면 함께 렌더한다", () => {
    const exp: Experience = {
      type: "INTERNSHIP",
      title: "OO 서비스 백엔드 인턴",
      months: 6,
      role: "백엔드 API 설계·구현",
      stack: ["Spring Boot", "JPA", "MySQL"],
      areas: ["API", "DB"],
      depth: "IMPLEMENTED",
    };
    render(<ExperienceCard experience={exp} />);

    expect(screen.getByText("인턴 · 6개월")).toBeInTheDocument();
    expect(screen.getByText("OO 서비스 백엔드 인턴")).toBeInTheDocument();
    expect(screen.getByText("백엔드 API 설계·구현")).toBeInTheDocument();
    expect(screen.getByText("Spring Boot")).toBeInTheDocument();
    expect(screen.getByText("API 개발")).toBeInTheDocument();
    expect(screen.getByText("데이터베이스")).toBeInTheDocument();
    expect(screen.getByText("직접 구현")).toBeInTheDocument();
  });

  it("선택 필드가 없으면 유형·제목만 렌더한다", () => {
    const exp: Experience = { type: "PROJECT", title: "사이드 프로젝트" };
    render(<ExperienceCard experience={exp} />);

    expect(screen.getByText("프로젝트")).toBeInTheDocument();
    expect(screen.getByText("사이드 프로젝트")).toBeInTheDocument();
  });

  it("depth가 없고 onAnalyzeDepth가 있으면 'AI 깊이 분석' 버튼을 보여주고 클릭 시 콜백을 호출한다", () => {
    const onAnalyzeDepth = vi.fn();
    const exp: Experience = { type: "PROJECT", title: "사이드 프로젝트" };
    render(<ExperienceCard experience={exp} onAnalyzeDepth={onAnalyzeDepth} />);

    const button = screen.getByText("✦ AI 깊이 분석");
    fireEvent.click(button);
    expect(onAnalyzeDepth).toHaveBeenCalledTimes(1);
  });

  it("depth가 이미 있으면 onAnalyzeDepth를 넘겨도 'AI 깊이 분석' 버튼을 보여주지 않는다", () => {
    const exp: Experience = { type: "PROJECT", title: "사이드 프로젝트", depth: "IMPLEMENTED" };
    render(<ExperienceCard experience={exp} onAnalyzeDepth={() => {}} />);

    expect(screen.queryByText("✦ AI 깊이 분석")).not.toBeInTheDocument();
  });
});
