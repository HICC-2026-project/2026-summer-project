"use client";

import { PASSER_COUNT, PRIMARY, READINESS, READINESS_RANK, RECOMMENDATIONS, USER_NAME } from "../../data";
import { dday, ddayColor } from "../../helpers";
import type { Target } from "../../types";

interface HomeTabProps {
  target: Target;
  onOpenDetail: (id: number) => void;
}

export function HomeTab({ target, onOpenDetail }: HomeTabProps) {
  const targetSummary = `${target.size} ${target.job}`;

  return (
    <div style={{ padding: "22px 20px 108px", animation: "cfUp .35s ease both" }}>
      <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", marginBottom: 22 }}>
        <div>
          <p style={{ fontSize: 14, color: "#61616C", margin: "0 0 4px", fontWeight: 500 }}>{USER_NAME}님, 안녕하세요 👋</p>
          <h1 style={{ fontSize: 23, fontWeight: 800, letterSpacing: "-0.02em", margin: 0, color: "#15141B", lineHeight: 1.3 }}>
            {targetSummary}
            <br />
            준비를 위한 추천이에요
          </h1>
        </div>
      </div>

      <div
        style={{
          background: `linear-gradient(140deg, ${PRIMARY}, color-mix(in srgb, ${PRIMARY} 62%, #7FA6FF))`,
          borderRadius: 22,
          padding: "20px 22px",
          color: "#fff",
          marginBottom: 14,
          boxShadow: `0 14px 30px color-mix(in srgb, ${PRIMARY} 34%, transparent)`,
          position: "relative",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            position: "absolute",
            right: -30,
            top: -30,
            width: 130,
            height: 130,
            borderRadius: "50%",
            background: "rgba(255,255,255,0.10)",
          }}
        />
        <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12.5, fontWeight: 600, opacity: 0.9, marginBottom: 12 }}>
          <span>✦</span> AI 종합 준비도
        </div>
        <div style={{ display: "flex", alignItems: "flex-end", gap: 14 }}>
          <div style={{ fontSize: 46, fontWeight: 800, lineHeight: 1, letterSpacing: "-0.03em" }}>
            {READINESS}
            <span style={{ fontSize: 20, fontWeight: 700 }}>점</span>
          </div>
          <div style={{ paddingBottom: 6 }}>
            <div style={{ fontSize: 13, fontWeight: 700 }}>상위 {READINESS_RANK}</div>
            <div style={{ fontSize: 12, opacity: 0.85, whiteSpace: "nowrap" }}>유사 합격자 {PASSER_COUNT}명 기준</div>
          </div>
        </div>
        <div style={{ marginTop: 14, height: 7, borderRadius: 999, background: "rgba(255,255,255,0.24)", overflow: "hidden" }}>
          <div
            style={{
              height: "100%",
              borderRadius: 999,
              background: "#fff",
              width: `${READINESS}%`,
              transformOrigin: "left",
              animation: "cfGrow .7s cubic-bezier(.2,.8,.2,1) both",
            }}
          />
        </div>
      </div>

      <div style={{ display: "flex", gap: 10, marginBottom: 26 }}>
        <div style={{ flex: 1, background: "#fff", border: "1px solid #EDEDF2", borderRadius: 16, padding: 14 }}>
          <div style={{ fontSize: 12, color: "#9797A1", fontWeight: 600, marginBottom: 6 }}>강점</div>
          <div style={{ fontSize: 15, fontWeight: 700, color: "#12A150" }}>학점 · 자격증</div>
        </div>
        <div style={{ flex: 1, background: "#fff", border: "1px solid #EDEDF2", borderRadius: 16, padding: 14 }}>
          <div style={{ fontSize: 12, color: "#9797A1", fontWeight: 600, marginBottom: 6 }}>보완 필요</div>
          <div style={{ fontSize: 15, fontWeight: 700, color: "#E5484D" }}>어학 · 실무경험</div>
        </div>
      </div>

      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 14 }}>
        <h2 style={{ fontSize: 18, fontWeight: 800, letterSpacing: "-0.02em", margin: 0, color: "#15141B" }}>
          지금 지원하면 좋은 활동
        </h2>
        <span
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 4,
            fontSize: 11.5,
            fontWeight: 700,
            color: PRIMARY,
            background: `color-mix(in srgb, ${PRIMARY} 11%, #fff)`,
            padding: "5px 9px",
            borderRadius: 999,
          }}
        >
          ✦ AI 추천
        </span>
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
        {RECOMMENDATIONS.map((r) => (
          <div
            key={r.id}
            onClick={() => onOpenDetail(r.id)}
            style={{
              background: "#fff",
              border: "1px solid #EDEDF2",
              borderRadius: 20,
              padding: "17px 18px",
              cursor: "pointer",
              transition: "transform .12s ease, box-shadow .12s ease",
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 12 }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 7, marginBottom: 8 }}>
                  <span style={{ fontSize: 11.5, fontWeight: 700, color: "#61616C", background: "#F1F0F6", padding: "4px 9px", borderRadius: 7 }}>
                    {r.type}
                  </span>
                  <span style={{ fontSize: 12, fontWeight: 600, color: ddayColor(r.deadline) }}>{dday(r.deadline)}</span>
                </div>
                <div style={{ fontSize: 16.5, fontWeight: 700, color: "#15141B", letterSpacing: "-0.01em", marginBottom: 3, lineHeight: 1.3 }}>
                  {r.name}
                </div>
                <div style={{ fontSize: 13, color: "#9797A1", fontWeight: 500 }}>{r.org}</div>
              </div>
              <div style={{ textAlign: "right", flexShrink: 0 }}>
                <div style={{ fontSize: 26, fontWeight: 800, color: PRIMARY, lineHeight: 1, letterSpacing: "-0.02em" }}>{r.score}</div>
                <div style={{ fontSize: 11, fontWeight: 700, color: "#B0B0BA", marginTop: 2 }}>매치</div>
              </div>
            </div>
            <div style={{ marginTop: 13, padding: "11px 13px", background: "#F8F7FC", borderRadius: 12, fontSize: 13, lineHeight: 1.5, color: "#4A4954" }}>
              <span style={{ color: PRIMARY, fontWeight: 700 }}>추천 이유 </span>
              {r.reason}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
