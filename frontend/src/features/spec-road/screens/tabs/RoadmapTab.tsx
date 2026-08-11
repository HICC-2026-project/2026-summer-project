"use client";

import { PRIMARY } from "../../data";
import { StateMessage } from "../../components/StateMessage";
import { dday, ddayColor, jobLabel } from "../../helpers";
import type { Priority, RoadmapMilestone, Target } from "../../types";

const PRIORITY_META: Record<Priority, { label: string; color: string; bg: string }> = {
  HIGH: { label: "지금 집중", color: PRIMARY, bg: `color-mix(in srgb, ${PRIMARY} 12%, #fff)` },
  MEDIUM: { label: "중요", color: "#B45309", bg: "#FBF0E4" },
  LOW: { label: "준비", color: "#6B7280", bg: "#F1F0F6" },
};

interface RoadmapTabProps {
  target: Target;
  roadmap: RoadmapMilestone[];
  roadmapLoading: boolean;
  roadmapError: boolean;
  roadmapLimitReached: boolean;
}

export function RoadmapTab({ target, roadmap, roadmapLoading, roadmapError, roadmapLimitReached }: RoadmapTabProps) {
  const targetSummary = `${target.size} ${jobLabel(target.job)}`;

  return (
    <div style={{ padding: "22px 20px 108px", animation: "cfUp .35s ease both" }}>
      <h1 style={{ fontSize: 23, fontWeight: 800, letterSpacing: "-0.02em", margin: "0 0 6px", color: "#15141B" }}>커리어 로드맵</h1>
      <p style={{ fontSize: 14, color: "#61616C", margin: "0 0 22px", lineHeight: 1.55 }}>
        {targetSummary} 목표까지, 시기별로 해야 할 일을 정리했어요.
      </p>

      {/* 하루 갱신 한도에 막혀 이전 로드맵을 보여주는 중이면 그 사실을 알린다.
          이 안내가 없으면 스펙을 바꾼 사용자가 "수정이 반영 안 된다"를 버그로 인지한다. */}
      {roadmapLimitReached && (
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
          오늘 로드맵 갱신 횟수(3회)를 모두 사용해서 이전 로드맵을 보여드리고 있어요. 내일 첫 방문 때 새로
          만들어져요.
        </div>
      )}

      {roadmapLoading ? (
        <StateMessage variant="loading" title="AI가 로드맵을 그리고 있어요…" />
      ) : roadmapError ? (
        <StateMessage
          variant="error"
          title="로드맵을 불러오지 못했어요"
          description="네트워크를 확인한 뒤 잠시 후 다시 시도해주세요."
        />
      ) : roadmap.length === 0 ? (
        <StateMessage
          title="아직 로드맵이 없어요"
          description="스펙과 목표 직무를 입력하면 시기별 로드맵을 만들어드려요."
        />
      ) : (
        <div style={{ position: "relative", paddingLeft: 6 }}>
          {roadmap.map((m, i) => {
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
                {/* phase는 목업 전용 필드 → 있을 때만 표시 (백엔드 응답엔 없음) */}
                {m.phase && (
                  <span style={{ fontSize: 11, fontWeight: 600, color: "#9797A1", background: "#F1F0F6", padding: "3px 8px", borderRadius: 6 }}>
                    {m.phase}
                  </span>
                )}
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

                {/* 백엔드가 RAG로 검증해 붙여주는 실제 DB 활동(이름·마감·지원 링크). 있을 때만 표시. */}
                {m.matchedActivities && m.matchedActivities.length > 0 && (
                  <div style={{ marginTop: 12, display: "flex", flexDirection: "column", gap: 8 }}>
                    {m.matchedActivities.map((a) => {
                      const body = (
                        <>
                          <span style={{ minWidth: 0, flex: 1 }}>
                            <span style={{ display: "block", fontSize: 13.5, fontWeight: 700, color: "#15141B", lineHeight: 1.35 }}>
                              {a.name}
                            </span>
                            {a.organization && (
                              <span style={{ display: "block", fontSize: 12, color: "#9797A1", fontWeight: 500 }}>{a.organization}</span>
                            )}
                          </span>
                          <span style={{ fontSize: 12, fontWeight: 700, color: ddayColor(a.deadline), flexShrink: 0 }}>{dday(a.deadline)}</span>
                        </>
                      );
                      const cardStyle = {
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        gap: 10,
                        padding: "10px 12px",
                        background: "#F8F7FC",
                        border: "1px solid #EDEDF2",
                        borderRadius: 12,
                        textDecoration: "none",
                      } as const;

                      // 지원 링크가 없는 활동도 있다. 그때 <a>로 감싸면 눌러도 아무 일이 없는
                      // "죽은 링크"가 되므로, 링크가 있을 때만 <a>로 렌더한다.
                      return a.url ? (
                        <a key={a.activityId} href={a.url} target="_blank" rel="noopener noreferrer" style={cardStyle}>
                          {body}
                        </a>
                      ) : (
                        <div key={a.activityId} style={cardStyle}>
                          {body}
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>
          );
          })}
        </div>
      )}
    </div>
  );
}
