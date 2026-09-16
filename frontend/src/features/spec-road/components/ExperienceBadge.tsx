"use client";

import { BADGE } from "../data";

// 경험이 GitHub 분석으로 자동 추가됐음을 알리는 작은 배지.
// 경험 항목을 렌더링하는 모든 화면(프로필 탭·온보딩 경험 목록)에서 공용으로 쓴다.
export function GithubExperienceBadge() {
  return (
    <span
      style={{
        display: "inline-block",
        marginLeft: 6,
        fontSize: 10.5,
        fontWeight: 700,
        color: BADGE.muted.color,
        background: BADGE.muted.bg,
        padding: "2px 7px",
        borderRadius: 999,
        verticalAlign: "middle",
      }}
    >
      GitHub 추정
    </span>
  );
}
