"use client";

import { COMPARE_ROWS, COMPARE_SCORE, COMPARE_TARGET, PASSER_COUNT, PRIMARY } from "../../data";
import type { RecommendationMeta } from "../../types";
import { StateMessage } from "../../components/StateMessage";

interface CompareTabProps {
  /** 비로그인 예시 화면 여부. 아래 수치는 전부 예시값이라 로그인 사용자에게는 보여주지 않는다. */
  isDemo: boolean;
  recMeta?: RecommendationMeta | null;
}

export function CompareTab({ isDemo, recMeta }: CompareTabProps) {
  const targetJob = isDemo ? COMPARE_TARGET : (recMeta?.targetJobName ?? "미설정");
  const passerCount = isDemo ? PASSER_COUNT : (recMeta?.similarPasserCount ?? 0);
  const matchScore = isDemo ? COMPARE_SCORE : (recMeta?.matchScore ?? 0);
  const compareRows = isDemo ? COMPARE_ROWS : (recMeta?.compareRows ?? []);
  
  // 데이터 로딩 중 (비로그인 아님 & API 아직 안 옴)
  if (!isDemo && !recMeta) {
    return (
      <div style={{ padding: "22px 20px 108px", animation: "cfUp .35s ease both" }}>
        <h1 style={{ fontSize: 23, fontWeight: 800, letterSpacing: "-0.02em", margin: "0 0 6px", color: "#15141B" }}>합격자 스펙 비교</h1>
        <p style={{ fontSize: 14, color: "#61616C", margin: "0 0 20px", lineHeight: 1.55 }}>
          내 스펙을 익명 합격자 데이터와 항목별로 비교해요.
        </p>
        <StateMessage
          title="비교 결과를 준비하고 있어요"
          description="항목별 분석 데이터를 불러오는 중입니다..."
        />
      </div>
    );
  }

  // 합격자 데이터 부족 (또는 에러)
  if (!isDemo && passerCount === 0) {
    return (
      <div style={{ padding: "22px 20px 108px", animation: "cfUp .35s ease both" }}>
        <h1 style={{ fontSize: 23, fontWeight: 800, letterSpacing: "-0.02em", margin: "0 0 6px", color: "#15141B" }}>합격자 스펙 비교</h1>
        <p style={{ fontSize: 14, color: "#61616C", margin: "0 0 20px", lineHeight: 1.55 }}>
          내 스펙을 익명 합격자 데이터와 항목별로 비교해요.
        </p>
        <StateMessage
          title="비교 가능한 데이터가 부족해요"
          description="입력해주신 직무와 학점에 딱 맞는 유사 합격자 데이터가 아직 부족하여 분석 결과를 제공해 드릴 수 없어요. 더 많은 데이터가 모일 때까지 기다려 주세요!"
        />
      </div>
    );
  }

  // 항목별 결과로 요약 문구를 만든다. 강조는 JSX로 처리해 HTML 주입을 피한다.
  const weakLabels = compareRows.filter((r) => r.status === "부족").map((r) => r.label);
  const strongLabels = compareRows.filter((r) => r.status === "충족").map((r) => r.label);
  const strongText = strongLabels.join("·");
  const weakText = weakLabels.join("·");

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
          비교 대상: <b style={{ color: "#15141B" }}>{targetJob}</b> · 유사 합격자{" "}
          <b style={{ color: PRIMARY }}>{passerCount}명</b>
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
            background: `conic-gradient(${PRIMARY} ${matchScore * 3.6}deg, #EDEDF2 0)`,
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
              {matchScore}
            </span>
            <span style={{ fontSize: 11, fontWeight: 700, color: "#9797A1", marginTop: 3 }}>종합 매치</span>
          </div>
        </div>
        <div style={{ flex: 1 }}>
          {/* '상위 N%'는 전체 사용자 분포를 알아야 나오는 값이라 매치 점수로 대신할 수 없다.
              백엔드가 주는 비교 대상 정보만 사실 그대로 표기한다. */}
          <div style={{ fontSize: 15, fontWeight: 800, color: "#15141B", marginBottom: 6, letterSpacing: "-0.01em" }}>
            유사 합격자 {passerCount}명과 비교했어요
          </div>
          <p style={{ fontSize: 13, color: "#61616C", lineHeight: 1.5, margin: 0 }}>
            {weakLabels.length === 0 ? (
              "항목별로 고르게 준비되어 있어요."
            ) : (
              <>
                {strongLabels.length > 0 && `합격선을 넘은 항목: ${strongText}. `}
                <b style={{ color: "#E5484D" }}>{weakText}</b> 항목을 보완하면 좋아요.
              </>
            )}
          </p>
        </div>
      </div>

      <div style={{ background: "#fff", border: "1px solid #EDEDF2", borderRadius: 22, padding: "8px 20px" }}>
        {compareRows.map((c, i) => {
          const ok = c.status === "충족";
          const statusColor = ok ? "#12A150" : "#E5484D";
          const statusBg = ok ? "#E7F6EE" : "#FCECEC";
          const divider = i === compareRows.length - 1 ? "transparent" : "#F1F0F6";

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
