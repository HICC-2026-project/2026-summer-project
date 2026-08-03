"use client";

import type { CSSProperties } from "react";
import { DEMO_USER_NAME, PRIMARY } from "../../data";
import { jobLabel } from "../../helpers";
import type { Spec, Target } from "../../types";

interface ProfileTabProps {
  spec: Spec;
  target: Target;
  nickname: string | null;
  /** 비로그인 예시 화면 여부. 저장할 계정이 없어 수정 대신 로그인을 유도한다. */
  isDemo: boolean;
  onEditSpec: () => void;
  onLogout: () => void;
}

function rowStyle(hasBorder: boolean): CSSProperties {
  return {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "15px 0",
    borderBottom: hasBorder ? "1px solid #F1F0F6" : "none",
  };
}

export function ProfileTab({ spec, target, nickname, isDemo, onEditSpec, onLogout }: ProfileTabProps) {
  const displayName = nickname ?? DEMO_USER_NAME;
  const targetSummary = `${target.size} ${jobLabel(target.job)}`;
  const certLabel = spec.certs.length ? spec.certs.join(", ") : "없음";
  const langEntries = Object.entries(spec.langScores).filter(([, score]) => score);
  const langLabel = langEntries.length ? langEntries.map(([type, score]) => `${type} ${score}`).join(", ") : "없음";

  return (
    <div style={{ padding: "22px 20px 108px", animation: "cfUp .35s ease both" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 15, marginBottom: 24 }}>
        <div
          style={{
            width: 60,
            height: 60,
            borderRadius: 20,
            background: `linear-gradient(145deg, ${PRIMARY}, color-mix(in srgb, ${PRIMARY} 60%, #7FA6FF))`,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "#fff",
            fontSize: 24,
            fontWeight: 800,
            flexShrink: 0,
          }}
        >
          {displayName.slice(0, 1)}
        </div>
        <div>
          <div style={{ fontSize: 20, fontWeight: 800, color: "#15141B", letterSpacing: "-0.02em" }}>{displayName}</div>
          <div style={{ fontSize: 13.5, color: "#61616C", marginTop: 2 }}>{targetSummary} 준비 중</div>
        </div>
      </div>

      {isDemo && (
        <div
          style={{
            display: "flex",
            gap: 9,
            padding: "12px 14px",
            marginBottom: 16,
            background: `color-mix(in srgb, ${PRIMARY} 7%, #fff)`,
            border: `1px solid color-mix(in srgb, ${PRIMARY} 18%, #fff)`,
            borderRadius: 14,
          }}
        >
          <span style={{ fontSize: 15, lineHeight: 1.4, flexShrink: 0 }}>👀</span>
          <span style={{ fontSize: 13, color: "#4A4954", lineHeight: 1.5 }}>
            예시 데이터를 보고 있어요. 로그인하면 내 스펙으로 추천을 받을 수 있어요.
          </span>
        </div>
      )}

      <div style={{ background: "#fff", border: "1px solid #EDEDF2", borderRadius: 20, padding: "6px 18px", marginBottom: 16 }}>
        <div style={rowStyle(true)}>
          <span style={{ fontSize: 14, color: "#61616C", fontWeight: 500 }}>학점</span>
          <span style={{ fontSize: 15, fontWeight: 700, color: "#15141B" }}>{spec.gpa} / {spec.gpaScale}</span>
        </div>
        <div style={{ ...rowStyle(true), alignItems: "flex-start", gap: 20 }}>
          <span style={{ fontSize: 14, color: "#61616C", fontWeight: 500, flexShrink: 0 }}>어학</span>
          <span style={{ fontSize: 14, fontWeight: 600, color: "#15141B", textAlign: "right", lineHeight: 1.5 }}>{langLabel}</span>
        </div>
        <div style={{ ...rowStyle(false), alignItems: "flex-start", gap: 20 }}>
          <span style={{ fontSize: 14, color: "#61616C", fontWeight: 500, flexShrink: 0 }}>자격증</span>
          <span style={{ fontSize: 14, fontWeight: 600, color: "#15141B", textAlign: "right", lineHeight: 1.5 }}>{certLabel}</span>
        </div>
      </div>

      {/* 예시 화면은 저장할 계정이 없어 수정 대신 로그인을 안내한다. */}
      <button
        type="button"
        onClick={onEditSpec}
        style={{
          width: "100%",
          height: 52,
          border: isDemo ? "none" : "1px solid #E1E0EA",
          borderRadius: 16,
          background: isDemo ? "#FEE500" : "#fff",
          color: isDemo ? "#191600" : "#15141B",
          fontSize: 15,
          fontWeight: 700,
          cursor: "pointer",
          marginBottom: 10,
        }}
      >
        {isDemo ? "카카오로 시작하고 내 스펙 입력하기" : "스펙 · 목표 수정하기"}
      </button>

      <p style={{ textAlign: "center", fontSize: 12, color: "#B0B0BA", margin: "22px 0 0" }}>
        <a
          href="#"
          onClick={(e) => {
            e.preventDefault();
            onLogout();
          }}
          style={{ color: "#B0B0BA" }}
        >
          {isDemo ? "처음 화면으로" : "로그아웃"}
        </a>
      </p>
    </div>
  );
}
