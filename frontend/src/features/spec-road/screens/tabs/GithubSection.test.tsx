import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { GithubSectionView } from "./GithubSection";
import type { GithubProfile } from "../../types";

// GithubSection의 상태 분기(미연결/PENDING/DONE/FAILED·RATE_LIMITED) 렌더 규칙을 고정한다.
// 데이터 요청·폴링은 컨테이너(GithubSection)가 맡고, 여기서는 순수 표시 컴포넌트만 검증한다
// (CompareTab.test.tsx와 같은 패턴 — props로 상태를 직접 주입).

function noop() {}

const doneProfile: GithubProfile = {
  connected: true,
  username: "octocat",
  status: "DONE",
  failureReason: null,
  analyzedAt: "2026-09-10T12:00:00Z",
  commitTotal: 120,
  activeMonths: 8,
  jobRatios: [
    { jobType: "BACKEND", label: "백엔드", ratio: 0.62 },
    { jobType: "OTHER", label: "기타", ratio: 0.1 },
  ],
  targetJobMatchRatio: 0.55,
  repos: [
    {
      name: "cool-api",
      primaryJob: "BACKEND",
      primaryJobLabel: "백엔드",
      commits: 42,
      firstCommitAt: "2025-01-01T00:00:00Z",
      lastCommitAt: "2026-08-01T00:00:00Z",
      mainLanguage: "Java",
    },
  ],
};

describe("GithubSectionView", () => {
  it("미연결이면 입력창·분석 시작 버튼·동의 문구를 보여주고, 입력이 비어있으면 버튼을 막는다", () => {
    render(
      <GithubSectionView
        profile={{ connected: false }}
        urlInput=""
        onUrlInputChange={noop}
        submitting={false}
        submitError={null}
        pollTimedOut={false}
        onSubmit={noop}
        onReanalyze={noop}
        onDisconnect={noop}
      />,
    );

    expect(screen.getByPlaceholderText("username 또는 github.com/username")).toBeInTheDocument();
    expect(screen.getByText("분석 시작")).toBeDisabled();
    expect(screen.getByText(/공개 레포만 분석합니다/)).toBeInTheDocument();
  });

  it("PENDING이면 분석 중 안내를 보여주고, 폴링 타임아웃이면 새로고침 안내로 바뀐다", () => {
    const pending: GithubProfile = {
      connected: true,
      username: "octocat",
      status: "PENDING",
      failureReason: null,
      analyzedAt: null,
      commitTotal: null,
      activeMonths: null,
      jobRatios: null,
      targetJobMatchRatio: null,
      repos: null,
    };

    const { rerender } = render(
      <GithubSectionView
        profile={pending}
        urlInput=""
        onUrlInputChange={noop}
        submitting={false}
        submitError={null}
        pollTimedOut={false}
        onSubmit={noop}
        onReanalyze={noop}
        onDisconnect={noop}
      />,
    );
    expect(screen.getByText("octocat 분석 중이에요")).toBeInTheDocument();
    expect(screen.queryByText(/새로고침해 주세요/)).not.toBeInTheDocument();

    rerender(
      <GithubSectionView
        profile={pending}
        urlInput=""
        onUrlInputChange={noop}
        submitting={false}
        submitError={null}
        pollTimedOut
        onSubmit={noop}
        onReanalyze={noop}
        onDisconnect={noop}
      />,
    );
    expect(screen.getByText(/새로고침해 주세요/)).toBeInTheDocument();
  });

  it("DONE이면 직무 비율·목표 직무 기여율·레포 목록을 보여주고, 재분석·연결 해제 버튼이 동작한다", () => {
    const onReanalyze = vi.fn();
    const onDisconnect = vi.fn();
    render(
      <GithubSectionView
        profile={doneProfile}
        urlInput=""
        onUrlInputChange={noop}
        submitting={false}
        submitError={null}
        pollTimedOut={false}
        onSubmit={noop}
        onReanalyze={onReanalyze}
        onDisconnect={onDisconnect}
      />,
    );

    expect(screen.getByText("백엔드")).toBeInTheDocument();
    expect(screen.getByText("62%")).toBeInTheDocument();
    expect(screen.getByText("목표 직무 관련 기여 55%")).toBeInTheDocument();
    expect(screen.getByText(/cool-api/)).toBeInTheDocument();

    fireEvent.click(screen.getByText("재분석"));
    expect(onReanalyze).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByText("연결 해제"));
    expect(onDisconnect).toHaveBeenCalledTimes(1);
  });

  it("FAILED는 failureReason을, RATE_LIMITED는 failureReason이 없으면 기본 안내를 보여준다", () => {
    const failed: GithubProfile = {
      connected: true,
      username: "octocat",
      status: "FAILED",
      failureReason: "비공개 계정이라 분석할 수 없어요.",
      analyzedAt: null,
      commitTotal: null,
      activeMonths: null,
      jobRatios: null,
      targetJobMatchRatio: null,
      repos: null,
    };
    const { rerender } = render(
      <GithubSectionView
        profile={failed}
        urlInput=""
        onUrlInputChange={noop}
        submitting={false}
        submitError={null}
        pollTimedOut={false}
        onSubmit={noop}
        onReanalyze={noop}
        onDisconnect={noop}
      />,
    );
    expect(screen.getByText("비공개 계정이라 분석할 수 없어요.")).toBeInTheDocument();
    expect(screen.getByText("다시 시도")).toBeInTheDocument();

    rerender(
      <GithubSectionView
        profile={{ ...failed, status: "RATE_LIMITED", failureReason: null }}
        urlInput=""
        onUrlInputChange={noop}
        submitting={false}
        submitError={null}
        pollTimedOut={false}
        onSubmit={noop}
        onReanalyze={noop}
        onDisconnect={noop}
      />,
    );
    expect(screen.getByText("GitHub 호출 한도에 걸렸어요. 잠시 후 재시도해 주세요.")).toBeInTheDocument();
  });
});
