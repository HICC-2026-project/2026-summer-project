"use client";

import { DEPTH_LABELS, PRIMARY } from "../data";
import { experienceTypeLabel } from "../helpers";
import type { Experience } from "../types";
import { AreaChips } from "./AreaChips";
import { GithubExperienceBadge } from "./ExperienceBadge";

interface ExperienceCardProps {
  experience: Experience;
}

// 경험 한 건의 표시 내용 — 유형 라벨·기간(N개월), 제목(+ GitHub 추정 배지), 역할 한 줄,
// 설명, 기술 스택·자동 태그(area) 칩, 깊이 배지를 그린다(E11 1단계).
// 온보딩 경험 목록 미리보기와 프로필 탭 경험 카드가 이 컴포넌트를 공유한다 —
// 한쪽만 고치고 다른 쪽을 빠뜨리는 걸 막기 위해서다.
export function ExperienceCard({ experience: exp }: ExperienceCardProps) {
  const depth = exp.depth ? DEPTH_LABELS[exp.depth] : null;
  const hasStack = (exp.stack?.length ?? 0) > 0;
  const hasAreas = (exp.areas?.length ?? 0) > 0;

  return (
    <div>
      <div style={{ fontSize: 12, fontWeight: 700, color: PRIMARY, marginBottom: 2 }}>
        {experienceTypeLabel(exp.type)}
        {exp.months != null && ` · ${exp.months}개월`}
      </div>
      <div style={{ fontSize: 14, fontWeight: 700, color: "#15141B" }}>
        {exp.title}
        {exp.source === "GITHUB" && <GithubExperienceBadge />}
      </div>
      {exp.role && (
        <div style={{ fontSize: 12.5, color: "#4A4954", fontWeight: 600, marginTop: 2 }}>{exp.role}</div>
      )}
      {exp.description && (
        <div style={{ fontSize: 12.5, color: "#61616C", marginTop: 2, lineHeight: 1.5 }}>{exp.description}</div>
      )}
      {(hasStack || hasAreas || depth) && (
        <div style={{ display: "flex", flexWrap: "wrap", gap: 6, marginTop: 8 }}>
          {exp.stack?.map((s) => (
            <span
              key={s}
              style={{
                display: "inline-block",
                fontSize: 11.5,
                fontWeight: 600,
                color: PRIMARY,
                background: `color-mix(in srgb, ${PRIMARY} 9%, #fff)`,
                border: `1px solid color-mix(in srgb, ${PRIMARY} 20%, #fff)`,
                padding: "3px 9px",
                borderRadius: 999,
              }}
            >
              {s}
            </span>
          ))}
          <AreaChips areas={exp.areas ?? []} />
          {depth && (
            <span
              style={{
                display: "inline-block",
                fontSize: 11.5,
                fontWeight: 700,
                color: "#61616C",
                background: "#fff",
                border: "1px solid #E1E0EA",
                padding: "3px 9px",
                borderRadius: 999,
              }}
            >
              {depth}
            </span>
          )}
        </div>
      )}
    </div>
  );
}
