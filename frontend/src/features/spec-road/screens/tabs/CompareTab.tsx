"use client";

import { DEMO_SPEC_POSITION, PRIMARY } from "../../data";
import type { AxisPosition, RecommendationMeta, SpecPosition } from "../../types";
import { percentileLabel } from "../../helpers";
import { StateMessage } from "../../components/StateMessage";

interface CompareTabProps {
  /** 비로그인 예시 화면 여부. 아래 수치는 전부 예시값이라 로그인 사용자에게는 보여주지 않는다. */
  isDemo: boolean;
  recMeta?: RecommendationMeta | null;
}

export function CompareTab({ isDemo, recMeta }: CompareTabProps) {
  const position: SpecPosition | null = isDemo ? DEMO_SPEC_POSITION : (recMeta?.specPosition ?? null);
  const unmatchedCerts = position?.unmatchedCertifications ?? [];
  const matchedCerts = position?.matchedCertifications ?? [];
  // OVERALL(전체 합격자 폴백) 기준일 때 "이 직무 합격자"라고 쓰면 바로 위 basisMessage
  // ("직무 구분 없이 전체 합격자와 비교")와 화면 안에서 모순된다 — 기준에 맞는 명칭을 쓴다.
  const basisNoun = position?.basis === "JOB" ? "이 직무 합격자" : "비교 기준 합격자";

  // 미매칭 자격증 고지는 비교 가능 여부와 무관한 정보다. 비교할 합격자가 없어
  // 아래에서 조기 반환하는 경우에도 이 안내만은 보여준다 — 백엔드도 같은 이유로
  // 데이터 부족(NONE)일 때 unmatchedCertifications만 채워서 내려준다.
  const unmatchedBanner = unmatchedCerts.length > 0 && (
    <div
      style={{
        marginTop: 14,
        padding: "12px 15px",
        border: "1px solid #EAE6F5",
        borderRadius: 14,
        background: "#F8F6FE",
        color: "#5B5566",
        fontSize: 12.5,
        lineHeight: 1.6,
      }}
    >
      <b style={{ color: "#15141B" }}>비교에 반영되지 않은 자격증이 있어요: </b>
      {unmatchedCerts.join(", ")}
      <br />
      {basisNoun} 데이터에 없는 자격증이에요. 오타라면 정확한 명칭으로 다시 입력해 주세요.
    </div>
  );

  // 데이터 로딩 중 (비로그인 아님 & API 아직 안 옴)
  if (!isDemo && !recMeta) {
    return (
      <div style={{ padding: "22px 20px 108px", animation: "cfUp .35s ease both" }}>
        <h1 style={{ fontSize: 23, fontWeight: 800, letterSpacing: "-0.02em", margin: "0 0 6px", color: "#15141B" }}>합격자 분포 속 내 위치</h1>
        <p style={{ fontSize: 14, color: "#61616C", margin: "0 0 20px", lineHeight: 1.55 }}>
          내 스펙이 익명 합격자 분포의 어디쯤인지, 뭘 보완하면 좋을지 보여드려요.
        </p>
        <StateMessage
          title="비교 결과를 준비하고 있어요"
          description="합격자 분포 데이터를 불러오는 중입니다..."
        />
      </div>
    );
  }

  // 합격자 데이터 부족 (또는 에러)
  if (!position || position.basis === "NONE") {
    return (
      <div style={{ padding: "22px 20px 108px", animation: "cfUp .35s ease both" }}>
        <h1 style={{ fontSize: 23, fontWeight: 800, letterSpacing: "-0.02em", margin: "0 0 6px", color: "#15141B" }}>합격자 분포 속 내 위치</h1>
        <p style={{ fontSize: 14, color: "#61616C", margin: "0 0 20px", lineHeight: 1.55 }}>
          내 스펙이 익명 합격자 분포의 어디쯤인지, 뭘 보완하면 좋을지 보여드려요.
        </p>
        <StateMessage
          title="비교 가능한 데이터가 부족해요"
          description="비교에 필요한 합격자 데이터가 아직 부족하여 분석 결과를 제공해 드릴 수 없어요. 더 많은 데이터가 모일 때까지 기다려 주세요!"
        />
        {unmatchedBanner}
      </div>
    );
  }

  return (
    <div style={{ padding: "22px 20px 108px", animation: "cfUp .35s ease both" }}>
      <h1 style={{ fontSize: 23, fontWeight: 800, letterSpacing: "-0.02em", margin: "0 0 6px", color: "#15141B" }}>합격자 분포 속 내 위치</h1>
      <p style={{ fontSize: 14, color: "#61616C", margin: "0 0 20px", lineHeight: 1.55 }}>
        내 스펙이 익명 합격자 분포의 어디쯤인지, 뭘 보완하면 좋을지 보여드려요.
      </p>

      {position.demoDataIncluded && (
        <div
          style={{
            marginBottom: 16,
            padding: "11px 14px",
            border: "1px solid #F0D8A8",
            borderRadius: 12,
            background: "#FFF9ED",
            color: "#79551F",
            fontSize: 12.5,
            lineHeight: 1.5,
          }}
        >
          발표 검증용 합성 샘플 또는 출처 미분류 데이터가 포함된 결과예요. 실제 합격 기준이나 합격 가능성을 의미하지 않아요.
        </div>
      )}

      {/* 비교 기준 — 폴백(직무 데이터 부족 → 전체 합격자) 여부까지 백엔드 문구가 정직하게 말해준다. */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          marginBottom: 14,
          padding: "12px 15px",
          background: `color-mix(in srgb, ${PRIMARY} 8%, #fff)`,
          borderRadius: 14,
        }}
      >
        <span style={{ fontSize: 16 }}>🎯</span>
        <div style={{ fontSize: 13.5, color: "#4A4954", lineHeight: 1.45, wordBreak: "keep-all" }}>
          {position.basisMessage}
        </div>
      </div>

      {/* 축별 위치 — 총점·가중치 없이 각 축의 분포 내 위치를 그대로 보여준다.
          막대 길이 = percentile(합격자 중 나보다 낮은 비율). 미입력이면 막대를 그리지 않는다. */}
      <div style={{ background: "#fff", border: "1px solid #EDEDF2", borderRadius: 22, padding: "8px 20px", marginBottom: 14 }}>
        {position.axes.map((a: AxisPosition, i: number) => {
          const hasValue = a.percentile != null;
          const badgeColor = hasValue ? (a.percentile! >= 50 ? "#12A150" : "#E5484D") : "#9797A1";
          const badgeBg = hasValue ? (a.percentile! >= 50 ? "#E7F6EE" : "#FCECEC") : "#F1F0F6";
          const divider = i === position.axes.length - 1 ? "transparent" : "#F1F0F6";

          return (
            <div key={a.axis} style={{ padding: "17px 0", borderBottom: `1px solid ${divider}` }}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 12 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <span style={{ fontSize: 14.5, fontWeight: 700, color: "#15141B" }}>{a.label}</span>
                  {/* 이 축 데이터를 가진 합격자 수 — 표본이 작으면 사용자가 감안할 수 있게 표기 */}
                  <span style={{ fontSize: 11, fontWeight: 600, color: "#B0B0BA" }}>합격자 {a.coverage}명 기준</span>
                </div>
                <span style={{ fontSize: 12, fontWeight: 700, color: badgeColor, background: badgeBg, padding: "4px 10px", borderRadius: 999 }}>
                  {percentileLabel(a.percentile)}
                </span>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 7 }}>
                <span style={{ width: 42, fontSize: 11.5, fontWeight: 700, color: PRIMARY, flexShrink: 0 }}>나</span>
                <div style={{ flex: 1, height: 9, borderRadius: 999, background: "#F1F0F6", overflow: "hidden" }}>
                  {hasValue && (
                    <div
                      style={{
                        height: "100%",
                        borderRadius: 999,
                        background: PRIMARY,
                        width: `${a.percentile}%`,
                        transformOrigin: "left",
                        animation: "cfGrow .6s ease both",
                      }}
                    />
                  )}
                </div>
                <span style={{ width: 72, textAlign: "right", fontSize: 13, fontWeight: 700, color: "#15141B", flexShrink: 0 }}>{a.myValue}</span>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <span style={{ width: 42, fontSize: 11.5, fontWeight: 700, color: "#B0B0BA", flexShrink: 0 }}>중앙값</span>
                {/* 중앙값은 정의상 분포의 50% 지점 — 막대도 항상 절반이다. 이 막대가 있어야
                    "내 막대가 중앙값 막대보다 긴가"로 위치를 직관적으로 읽을 수 있다. */}
                <div style={{ flex: 1, height: 9, borderRadius: 999, background: "#F1F0F6", overflow: "hidden" }}>
                  <div
                    style={{
                      height: "100%",
                      borderRadius: 999,
                      background: "#C9C7D6",
                      width: "50%",
                      transformOrigin: "left",
                      animation: "cfGrow .6s ease both",
                    }}
                  />
                </div>
                <span style={{ width: 72, textAlign: "right", fontSize: 13, fontWeight: 600, color: "#9797A1", flexShrink: 0 }}>{a.medianValue}</span>
              </div>
            </div>
          );
        })}
      </div>

      {/* 갭 — 이 직무 합격자 다수가 가진 것 중 나에게 없는 것. "다음 할 일" 리스트다. */}
      <div style={{ background: "#fff", border: "1px solid #EDEDF2", borderRadius: 22, padding: "18px 20px" }}>
        <div style={{ fontSize: 14.5, fontWeight: 800, color: "#15141B", marginBottom: 4 }}>지금 메우면 좋은 갭</div>
        <p style={{ fontSize: 12.5, color: "#61616C", margin: "0 0 12px", lineHeight: 1.5 }}>
          {basisNoun} 다수가 보유했지만 아직 나에게 없는 자격증이에요.
        </p>
        {position.gaps.length === 0 ? (
          // 갭이 비는 데는 두 가지 이유가 있다: (a) 다수 보유 자격증을 사용자가 전부 가짐,
          // (b) 애초에 보유율 기준(20%)을 넘는 자격증이 집계되지 않음. (b)에서 칭찬 문구를
          // 띄우면 자격증이 하나도 없는 사용자에게 "모두 갖췄다"고 말하는 오답이 된다 —
          // 매칭 보유가 하나라도 있을 때만 칭찬하고, 아니면 사실대로 말한다.
          matchedCerts.length > 0 ? (
            <div style={{ fontSize: 13, color: "#12A150", fontWeight: 700 }}>
              합격자 다수가 보유한 자격증을 모두 갖췄어요 👏
            </div>
          ) : (
            <div style={{ fontSize: 13, color: "#61616C" }}>
              아직 이 비교 기준에서 집계된 주요 자격증이 없어요. 합격자 데이터가 쌓이면 여기에 갭이 표시돼요.
            </div>
          )
        ) : (
          position.gaps.map((g, i) => (
            <div key={g.name} style={{ display: "flex", alignItems: "center", gap: 10, padding: "9px 0", borderTop: i === 0 ? "none" : "1px solid #F1F0F6" }}>
              <span style={{ fontSize: 13.5, fontWeight: 700, color: "#15141B", flex: 1 }}>{g.name}</span>
              <div style={{ width: 90, height: 8, borderRadius: 999, background: "#F1F0F6", overflow: "hidden", flexShrink: 0 }}>
                <div style={{ height: "100%", borderRadius: 999, background: PRIMARY, width: `${g.holderRatePercent}%` }} />
              </div>
              <span style={{ fontSize: 12, fontWeight: 700, color: "#61616C", width: 92, textAlign: "right", flexShrink: 0 }}>
                합격자 {g.holderRatePercent}% 보유
              </span>
            </div>
          ))
        )}
        {matchedCerts.length > 0 && (
          <div style={{ marginTop: 12, paddingTop: 12, borderTop: "1px solid #F1F0F6", fontSize: 12.5, color: "#61616C", lineHeight: 1.6 }}>
            <b style={{ color: "#12A150" }}>이미 보유 ✓</b> {matchedCerts.join(", ")}
          </div>
        )}
      </div>

      {unmatchedBanner}
    </div>
  );
}
