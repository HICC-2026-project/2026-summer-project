"use client";

import { COMPARE_DEG, COMPARE_ROWS, COMPARE_SCORE, COMPARE_TARGET, PASSER_COUNT, PRIMARY, READINESS_RANK } from "../../data";

export function CompareTab() {
  return (
    <div style={{ padding: "22px 20px 108px", animation: "cfUp .35s ease both" }}>
      <h1 style={{ fontSize: 23, fontWeight: 800, letterSpacing: "-0.02em", margin: "0 0 6px", color: "#15141B" }}>합격자 스펙 비교</h1>
      <p style={{ fontSize: 14, color: "#61616C", margin: "0 0 20px", lineHeight: 1.55 }}>
        내 스펙을 익명 합격자 데이터와 항목별로 비교해요.
      </p>

      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          marginBottom: 16,
          padding: "12px 15px",
          background: `color-mix(in srgb, ${PRIMARY} 8%, #fff)`,
          borderRadius: 14,
        }}
      >
        <span style={{ fontSize: 16 }}>🎯</span>
        <div style={{ fontSize: 13.5, color: "#4A4954", lineHeight: 1.45 }}>
          비교 대상: <b style={{ color: "#15141B" }}>{COMPARE_TARGET}</b> · 유사 합격자{" "}
          <b style={{ color: PRIMARY }}>{PASSER_COUNT}명</b>
        </div>
      </div>

      <div
        style={{
          background: "#fff",
          border: "1px solid #EDEDF2",
          borderRadius: 22,
          padding: 24,
          display: "flex",
          alignItems: "center",
          gap: 22,
          marginBottom: 14,
        }}
      >
        <div
          style={{
            position: "relative",
            width: 108,
            height: 108,
            flexShrink: 0,
            borderRadius: "50%",
            background: `conic-gradient(${PRIMARY} ${COMPARE_DEG}deg, #EDEDF2 0)`,
          }}
        >
          <div
            style={{
              position: "absolute",
              inset: 9,
              borderRadius: "50%",
              background: "#fff",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <span style={{ fontSize: 32, fontWeight: 800, color: "#15141B", lineHeight: 1, letterSpacing: "-0.03em" }}>
              {COMPARE_SCORE}
            </span>
            <span style={{ fontSize: 11, fontWeight: 700, color: "#9797A1", marginTop: 3 }}>종합 매치</span>
          </div>
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 15, fontWeight: 800, color: "#15141B", marginBottom: 6, letterSpacing: "-0.01em" }}>
            상위 {READINESS_RANK} 수준
          </div>
          <p style={{ fontSize: 13, color: "#61616C", lineHeight: 1.5, margin: 0 }}>
            학점·자격증은 합격선을 넘었어요. <b style={{ color: "#E5484D" }}>어학·경험</b>을 보완하면 안정권이에요.
          </p>
        </div>
      </div>

      <div style={{ background: "#fff", border: "1px solid #EDEDF2", borderRadius: 22, padding: "8px 20px" }}>
        {COMPARE_ROWS.map((c, i) => {
          const ok = c.status === "충족";
          const statusColor = ok ? "#12A150" : "#E5484D";
          const statusBg = ok ? "#E7F6EE" : "#FCECEC";
          const divider = i === COMPARE_ROWS.length - 1 ? "transparent" : "#F1F0F6";

          return (
            <div key={c.label} style={{ padding: "17px 0", borderBottom: `1px solid ${divider}` }}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 12 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <span style={{ fontSize: 14.5, fontWeight: 700, color: "#15141B" }}>{c.label}</span>
                  <span style={{ fontSize: 11, fontWeight: 600, color: "#B0B0BA" }}>{c.weight}</span>
                </div>
                <span style={{ fontSize: 12, fontWeight: 700, color: statusColor, background: statusBg, padding: "4px 10px", borderRadius: 999 }}>
                  {c.status}
                </span>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 7 }}>
                <span style={{ width: 42, fontSize: 11.5, fontWeight: 700, color: PRIMARY, flexShrink: 0 }}>나</span>
                <div style={{ flex: 1, height: 9, borderRadius: 999, background: "#F1F0F6", overflow: "hidden" }}>
                  <div
                    style={{
                      height: "100%",
                      borderRadius: 999,
                      background: PRIMARY,
                      width: `${c.myPct}%`,
                      transformOrigin: "left",
                      animation: "cfGrow .6s ease both",
                    }}
                  />
                </div>
                <span style={{ width: 58, textAlign: "right", fontSize: 13, fontWeight: 700, color: "#15141B", flexShrink: 0 }}>{c.myVal}</span>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <span style={{ width: 42, fontSize: 11.5, fontWeight: 700, color: "#B0B0BA", flexShrink: 0 }}>합격자</span>
                <div style={{ flex: 1, height: 9, borderRadius: 999, background: "#F1F0F6", overflow: "hidden" }}>
                  <div
                    style={{
                      height: "100%",
                      borderRadius: 999,
                      background: "#C9C7D6",
                      width: `${c.avgPct}%`,
                      transformOrigin: "left",
                      animation: "cfGrow .6s ease both",
                    }}
                  />
                </div>
                <span style={{ width: 58, textAlign: "right", fontSize: 13, fontWeight: 600, color: "#9797A1", flexShrink: 0 }}>{c.avgVal}</span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
