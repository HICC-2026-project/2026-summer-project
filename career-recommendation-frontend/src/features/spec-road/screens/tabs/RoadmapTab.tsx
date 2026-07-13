"use client";

import { PRIMARY, ROADMAP } from "../../data";
import type { Priority, Target } from "../../types";

const PRIORITY_META: Record<Priority, { label: string; color: string; bg: string }> = {
  HIGH: { label: "지금 집중", color: PRIMARY, bg: `color-mix(in srgb, ${PRIMARY} 12%, #fff)` },
  MEDIUM: { label: "중요", color: "#B45309", bg: "#FBF0E4" },
  LOW: { label: "준비", color: "#6B7280", bg: "#F1F0F6" },
};

interface RoadmapTabProps {
  target: Target;
}

export function RoadmapTab({ target }: RoadmapTabProps) {
  const targetSummary = `${target.size} ${target.job}`;

  return (
    <div style={{ padding: "22px 20px 108px", animation: "cfUp .35s ease both" }}>
      <h1 style={{ fontSize: 23, fontWeight: 800, letterSpacing: "-0.02em", margin: "0 0 6px", color: "#15141B" }}>커리어 로드맵</h1>
      <p style={{ fontSize: 14, color: "#61616C", margin: "0 0 22px", lineHeight: 1.55 }}>
        {targetSummary} 목표까지, 시기별로 해야 할 일을 정리했어요.
      </p>

      <div style={{ position: "relative", paddingLeft: 6 }}>
        {ROADMAP.map((m, i) => {
          const meta = PRIORITY_META[m.priority];
          const dotBg = m.current ? PRIMARY : "#fff";
          const dotBorder = m.current ? PRIMARY : "#D9D8E4";
          const cardBorder = m.current ? `color-mix(in srgb, ${PRIMARY} 35%, #fff)` : "#EDEDF2";

          return (
            <div key={i} style={{ position: "relative", paddingLeft: 32, paddingBottom: 20 }}>
              <div style={{ position: "absolute", left: 9, top: 22, bottom: -4, width: 2, background: "#E7E6EF" }} />
              <div
                style={{
                  position: "absolute",
                  left: 0,
                  top: 3,
                  width: 20,
                  height: 20,
                  borderRadius: "50%",
                  background: dotBg,
                  border: `3px solid ${dotBorder}`,
                  boxSizing: "border-box",
                }}
              />
              <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8 }}>
                <span style={{ fontSize: 13, fontWeight: 800, color: "#15141B" }}>{m.period}</span>
                <span style={{ fontSize: 11, fontWeight: 600, color: "#9797A1", background: "#F1F0F6", padding: "3px 8px", borderRadius: 6 }}>
                  {m.phase}
                </span>
              </div>
              <div style={{ background: "#fff", border: `1px solid ${cardBorder}`, borderRadius: 18, padding: 16 }}>
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 8 }}>
                  <span style={{ fontSize: 11.5, fontWeight: 700, color: meta.color, background: meta.bg, padding: "4px 9px", borderRadius: 999 }}>
                    {meta.label}
                  </span>
                  <span style={{ fontSize: 12, fontWeight: 600, color: "#B0B0BA" }}>{m.current ? "진행 중" : ""}</span>
                </div>
                <div style={{ fontSize: 16, fontWeight: 700, color: "#15141B", letterSpacing: "-0.01em", marginBottom: 6, lineHeight: 1.35 }}>
                  {m.activity}
                </div>
                <div style={{ fontSize: 13, color: "#61616C", lineHeight: 1.5 }}>{m.reason}</div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
