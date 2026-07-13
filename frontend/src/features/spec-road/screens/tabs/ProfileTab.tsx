"use client";

import type { CSSProperties } from "react";
import { PRIMARY, USER_NAME } from "../../data";
import type { Spec, Target } from "../../types";

interface ProfileTabProps {
  spec: Spec;
  target: Target;
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

export function ProfileTab({ spec, target, onEditSpec, onLogout }: ProfileTabProps) {
  const targetSummary = `${target.size} ${target.job}`;
  const certLabel = spec.certs.length ? spec.certs.join(", ") : "없음";
  const expLabel = spec.exps.length ? spec.exps.join(", ") : "없음";

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
          {USER_NAME.slice(0, 1)}
        </div>
        <div>
          <div style={{ fontSize: 20, fontWeight: 800, color: "#15141B", letterSpacing: "-0.02em" }}>{USER_NAME}</div>
          <div style={{ fontSize: 13.5, color: "#61616C", marginTop: 2 }}>{targetSummary} 준비 중</div>
        </div>
      </div>

      <div style={{ background: "#fff", border: "1px solid #EDEDF2", borderRadius: 20, padding: "6px 18px", marginBottom: 16 }}>
        <div style={rowStyle(true)}>
          <span style={{ fontSize: 14, color: "#61616C", fontWeight: 500 }}>학점</span>
          <span style={{ fontSize: 15, fontWeight: 700, color: "#15141B" }}>{spec.gpa} / {spec.gpaScale}</span>
        </div>
        <div style={rowStyle(true)}>
          <span style={{ fontSize: 14, color: "#61616C", fontWeight: 500 }}>어학</span>
          <span style={{ fontSize: 15, fontWeight: 700, color: "#15141B" }}>
            {spec.langType} {spec.langScore}
          </span>
        </div>
        <div style={{ ...rowStyle(true), alignItems: "flex-start", gap: 20 }}>
          <span style={{ fontSize: 14, color: "#61616C", fontWeight: 500, flexShrink: 0 }}>자격증</span>
          <span style={{ fontSize: 14, fontWeight: 600, color: "#15141B", textAlign: "right", lineHeight: 1.5 }}>{certLabel}</span>
        </div>
        <div style={{ ...rowStyle(false), alignItems: "flex-start", gap: 20 }}>
          <span style={{ fontSize: 14, color: "#61616C", fontWeight: 500, flexShrink: 0 }}>경험</span>
          <span style={{ fontSize: 14, fontWeight: 600, color: "#15141B", textAlign: "right", lineHeight: 1.5 }}>{expLabel}</span>
        </div>
      </div>

      <button
        type="button"
        onClick={onEditSpec}
        style={{
          width: "100%",
          height: 52,
          border: "1px solid #E1E0EA",
          borderRadius: 16,
          background: "#fff",
          color: "#15141B",
          fontSize: 15,
          fontWeight: 700,
          cursor: "pointer",
          marginBottom: 10,
        }}
      >
        스펙 · 목표 수정하기
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
          로그아웃
        </a>
      </p>
    </div>
  );
}
