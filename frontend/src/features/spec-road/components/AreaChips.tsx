"use client";

import { BADGE } from "../data";
import { areaLabel } from "../helpers";

interface AreaChipsProps {
  areas: string[];
}

// 자동 태그(area) 칩. stack 칩과 한눈에 구분되도록 무채색/저채도로 그린다.
// 경험 카드(ExperienceCard)와 GitHub 레포 카드(GithubSection)가 공용으로 쓴다.
export function AreaChips({ areas }: AreaChipsProps) {
  if (areas.length === 0) return null;
  return (
    <>
      {areas.map((a) => (
        <span
          key={a}
          style={{
            display: "inline-block",
            fontSize: 11.5,
            fontWeight: 600,
            color: BADGE.muted.color,
            background: BADGE.muted.bg,
            padding: "3px 9px",
            borderRadius: 999,
          }}
        >
          {areaLabel(a)}
        </span>
      ))}
    </>
  );
}
