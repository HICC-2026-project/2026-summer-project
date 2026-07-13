"use client";

import { PRIMARY, RECOMMENDATIONS } from "../data";
import { dday, ddayColor, fmtDate } from "../helpers";

interface DetailSheetProps {
  recommendationId: number;
  onClose: () => void;
  onCompare: () => void;
}

export function DetailSheet({ recommendationId, onClose, onCompare }: DetailSheetProps) {
  const rec = RECOMMENDATIONS.find((r) => r.id === recommendationId);
  if (!rec) return null;

  const scoreDeg = rec.score * 3.6;

  return (
    <>
      <div
        onClick={onClose}
        style={{
          position: "absolute",
          inset: 0,
          background: "rgba(20,18,40,0.42)",
          zIndex: 40,
          animation: "cfFade .2s ease both",
        }}
      />
      <div
        style={{
          position: "absolute",
          left: 0,
          right: 0,
          bottom: 0,
          zIndex: 41,
          maxHeight: "90%",
          background: "#fff",
          borderRadius: "26px 26px 0 0",
          display: "flex",
          flexDirection: "column",
          animation: "cfSheet .32s cubic-bezier(.2,.8,.2,1) both",
        }}
      >
        <div style={{ padding: "12px 0 4px", display: "flex", justifyContent: "center", flexShrink: 0 }}>
          <div style={{ width: 40, height: 5, borderRadius: 999, background: "#E1E0EA" }} />
        </div>
        <div className="cf-scroll" style={{ overflowY: "auto", padding: "12px 24px 20px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 7, marginBottom: 12 }}>
            <span style={{ fontSize: 12, fontWeight: 700, color: "#61616C", background: "#F1F0F6", padding: "5px 11px", borderRadius: 8 }}>
              {rec.type}
            </span>
            <span style={{ fontSize: 12.5, fontWeight: 700, color: ddayColor(rec.deadline) }}>{dday(rec.deadline)}</span>
          </div>
          <h2 style={{ fontSize: 23, fontWeight: 800, color: "#15141B", letterSpacing: "-0.02em", margin: "0 0 5px", lineHeight: 1.3 }}>
            {rec.name}
          </h2>
          <div style={{ fontSize: 14, color: "#9797A1", fontWeight: 500, marginBottom: 20 }}>
            {rec.org} · 마감 {fmtDate(rec.deadline)}
          </div>

          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: 18,
              padding: "18px 20px",
              background: `color-mix(in srgb, ${PRIMARY} 6%, #fff)`,
              borderRadius: 20,
              marginBottom: 20,
            }}
          >
            <div
              style={{
                position: "relative",
                width: 84,
                height: 84,
                flexShrink: 0,
                borderRadius: "50%",
                background: `conic-gradient(${PRIMARY} ${scoreDeg}deg, #E7E5F3 0)`,
              }}
            >
              <div
                style={{
                  position: "absolute",
                  inset: 8,
                  borderRadius: "50%",
                  background: "#fff",
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <span style={{ fontSize: 26, fontWeight: 800, color: PRIMARY, lineHeight: 1 }}>{rec.score}</span>
                <span style={{ fontSize: 10, fontWeight: 700, color: "#B0B0BA" }}>매치</span>
              </div>
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 14.5, fontWeight: 800, color: "#15141B", marginBottom: 4 }}>나와 잘 맞는 활동이에요</div>
              <div style={{ fontSize: 13, color: "#61616C", lineHeight: 1.5 }}>유사 합격자 {rec.passers}명의 스펙과 비교한 결과예요.</div>
            </div>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 12 }}>
            <span style={{ fontSize: 15, fontWeight: 800, color: "#15141B" }}>왜 추천했나요?</span>
            <span style={{ fontSize: 11, fontWeight: 700, color: PRIMARY }}>✦ AI 분석</span>
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: 10, marginBottom: 22 }}>
            {rec.bullets.map((b, i) => (
              <div key={i} style={{ display: "flex", gap: 10, alignItems: "flex-start" }}>
                <span
                  style={{
                    width: 22,
                    height: 22,
                    borderRadius: 7,
                    background: `color-mix(in srgb, ${PRIMARY} 12%, #fff)`,
                    color: PRIMARY,
                    fontSize: 13,
                    fontWeight: 800,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    flexShrink: 0,
                    marginTop: 1,
                  }}
                >
                  ✓
                </span>
                <span style={{ fontSize: 14, color: "#4A4954", lineHeight: 1.55, flex: 1 }}>{b}</span>
              </div>
            ))}
          </div>

          <div style={{ display: "flex", flexWrap: "wrap", gap: 7, marginBottom: 8 }}>
            {rec.tags.map((t) => (
              <span key={t} style={{ fontSize: 12.5, fontWeight: 600, color: "#61616C", background: "#F1F0F6", padding: "6px 12px", borderRadius: 999 }}>
                #{t}
              </span>
            ))}
          </div>
        </div>
        <div style={{ padding: "12px 24px calc(20px + env(safe-area-inset-bottom))", borderTop: "1px solid #F1F0F6", display: "flex", gap: 10, flexShrink: 0 }}>
          <button
            type="button"
            onClick={onCompare}
            style={{ flex: 1, height: 54, border: "1px solid #E1E0EA", borderRadius: 16, background: "#fff", color: "#15141B", fontSize: 15, fontWeight: 700, cursor: "pointer" }}
          >
            합격자 비교
          </button>
          <button
            type="button"
            onClick={onClose}
            style={{ flex: 1.4, height: 54, border: "none", borderRadius: 16, background: PRIMARY, color: "#fff", fontSize: 15, fontWeight: 700, cursor: "pointer" }}
          >
            관심 활동 담기
          </button>
        </div>
      </div>
    </>
  );
}
