"use client";

import { useEffect, useState } from "react";
import { getActivity } from "../api";
import { PRIMARY } from "../data";
import { dday, ddayColor, fmtDate } from "../helpers";
import type { ActivityDetailResponse, Recommendation, RecommendationMeta } from "../types";

interface DetailSheetProps {
  recommendationId: string | number;
  recommendations: Recommendation[];
  recMeta: RecommendationMeta | null;
  onClose: () => void;
}

export function DetailSheet({ recommendationId, recommendations, recMeta, onClose }: DetailSheetProps) {
  const rec = recommendations.find((r) => r.id === recommendationId);

  // 추천 응답에는 지원 링크·주최·태그가 없어, 활동 상세를 따로 불러와 채운다.
  // (둘러보기의 목업 id는 실제 활동이 아니라 요청하지 않는다)
  const [detail, setDetail] = useState<ActivityDetailResponse | null>(null);

  useEffect(() => {
    if (typeof recommendationId !== "string") return;

    let cancelled = false;
    getActivity(recommendationId)
      .then((res) => {
        if (!cancelled) setDetail(res);
      })
      .catch(() => {
        // 링크는 부가 정보라, 실패해도 상세 시트는 그대로 보여준다.
      });
    return () => {
      cancelled = true;
    };
  }, [recommendationId]);

  // 다른 활동을 열었을 때 이전 응답이 잠깐 섞이지 않도록 id가 일치할 때만 사용한다.
  const activityDetail = detail?.id === recommendationId ? detail : null;

  if (!rec) return null;

  // 개별 활동 점수(score)는 목업(둘러보기)에만 있다.
  // 실 API는 활동별 점수를 주지 않는다(위치·갭 요약뿐). 응답 최상단 값을 이 자리에 넣으면
  // 어떤 활동을 열어도 같은 숫자가 떠서 "이 활동과의 매치도"로 오해하게 된다. 그래서 숨긴다.
  const activityScore = rec.score ?? null;
  const scoreDeg = (activityScore ?? 0) * 3.6;
  // 상세 근거: 목업은 bullets 배열, 실 API는 단일 reason 문자열을 준다.
  const bullets = rec.bullets && rec.bullets.length > 0 ? rec.bullets : [rec.reason];
  // 비교 안내 문구: 목업은 유사 합격자 수, 실 API는 specPosition의 비교 기준 문구.
  const matchSubtitle =
    rec.passers != null
      ? `유사 합격자 ${rec.passers}명의 스펙과 비교한 결과예요.`
      : (recMeta?.specPosition?.basisMessage ?? "나와 잘 맞는 활동이에요.");

  // 주최·태그·지원 링크는 목업(둘러보기)과 활동 상세 중 있는 쪽을 쓴다.
  const organization = rec.org ?? activityDetail?.organization ?? null;
  const tags = rec.tags && rec.tags.length > 0 ? rec.tags : (activityDetail?.tags ?? []);
  const applyUrl = activityDetail?.url ?? null;

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
            {organization ? `${organization} · ` : ""}마감 {fmtDate(rec.deadline)}
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
            {activityScore != null && (
              <div
                style={{
                  position: "relative",
                  width: 84,
                  height: 84,
                  flexShrink: 0,
                  borderRadius: "50%",
                  background: `conic-gradient(${PRIMARY} ${scoreDeg}deg, #DCE7FE 0)`,
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
                  <span style={{ fontSize: 26, fontWeight: 800, color: PRIMARY, lineHeight: 1 }}>{activityScore}</span>
                  <span style={{ fontSize: 10, fontWeight: 700, color: "#B0B0BA" }}>매치</span>
                </div>
              </div>
            )}
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 14.5, fontWeight: 800, color: "#15141B", marginBottom: 4 }}>나와 잘 맞는 활동이에요</div>
              <div style={{ fontSize: 13, color: "#61616C", lineHeight: 1.5 }}>{matchSubtitle}</div>
            </div>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 12 }}>
            <span style={{ fontSize: 15, fontWeight: 800, color: "#15141B" }}>왜 추천했나요?</span>
            <span style={{ fontSize: 11, fontWeight: 700, color: PRIMARY }}>✦ AI 분석</span>
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: 10, marginBottom: 22 }}>
            {bullets.map((b, i) => (
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

          {tags.length > 0 && (
            <div style={{ display: "flex", flexWrap: "wrap", gap: 7, marginBottom: 8 }}>
              {tags.map((t) => (
                <span key={t} style={{ fontSize: 12.5, fontWeight: 600, color: "#61616C", background: "#F1F0F6", padding: "6px 12px", borderRadius: 999 }}>
                  #{t}
                </span>
              ))}
            </div>
          )}
        </div>
        <div style={{ padding: "12px 24px calc(20px + env(safe-area-inset-bottom))", borderTop: "1px solid #F1F0F6", display: "flex", gap: 10, flexShrink: 0 }}>
          {/* 지원 링크는 활동 상세에 있을 때만 노출한다. */}
          {applyUrl && (
            <a
              href={applyUrl}
              target="_blank"
              rel="noopener noreferrer"
              style={{
                flex: 1,
                height: 54,
                border: "1px solid #E1E0EA",
                borderRadius: 16,
                background: "#fff",
                color: "#15141B",
                fontSize: 15,
                fontWeight: 700,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                textDecoration: "none",
              }}
            >
              지원하기 ↗
            </a>
          )}
          <button
            type="button"
            onClick={onClose}
            style={{ flex: 1.4, height: 54, border: "none", borderRadius: 16, background: PRIMARY, color: "#fff", fontSize: 15, fontWeight: 700, cursor: "pointer" }}
          >
            {/* 관심 활동 저장 기능은 아직 없어, 실제 동작(닫기)과 일치하는 문구를 쓴다. */}
            닫기
          </button>
        </div>
      </div>
    </>
  );
}
