"use client";

import type { CSSProperties } from "react";
import { Chip } from "../components/Chip";
import {
  CERT_OPTIONS,
  EXP_OPTIONS,
  INDUSTRY_OPTIONS,
  JOB_OPTIONS,
  LANG_TYPES,
  PRIMARY,
  SIZE_OPTIONS,
} from "../data";
import { chipStyle } from "../helpers";
import type { OnboardStep, Spec, Target } from "../types";

interface OnboardingScreenProps {
  step: OnboardStep;
  spec: Spec;
  target: Target;
  onBack: () => void;
  onNext: () => void;
  onSetGpa: (v: number) => void;
  onSetLangType: (v: string) => void;
  onSetLangScore: (v: number) => void;
  onToggleCert: (v: string) => void;
  onToggleExp: (v: string) => void;
  onSetJob: (v: string) => void;
  onSetSize: (v: string) => void;
  onSetIndustry: (v: string) => void;
}

const cardStyle: CSSProperties = {
  background: "#fff",
  border: "1px solid #EDEDF2",
  borderRadius: 20,
  padding: 18,
  marginBottom: 12,
};

const fieldLabelStyle: CSSProperties = {
  display: "block",
  fontSize: 13,
  fontWeight: 700,
  color: "#61616C",
  marginBottom: 10,
};

const numberInputStyle: CSSProperties = {
  border: "none",
  borderBottom: `2px solid ${PRIMARY}`,
  background: "transparent",
  fontSize: 28,
  fontWeight: 800,
  color: "#15141B",
  padding: "2px 0",
  outline: "none",
};

export function OnboardingScreen({
  step,
  spec,
  target,
  onBack,
  onNext,
  onSetGpa,
  onSetLangType,
  onSetLangScore,
  onToggleCert,
  onToggleExp,
  onSetJob,
  onSetSize,
  onSetIndustry,
}: OnboardingScreenProps) {
  const onboardPct = step === 0 ? "50%" : "100%";
  const onboardCta = step === 0 ? "다음" : "분석 시작하기";

  return (
    <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", background: "#F6F6F9" }}>
      <div style={{ padding: "18px 20px 12px", display: "flex", alignItems: "center", gap: 14 }}>
        <button
          type="button"
          onClick={onBack}
          style={{
            width: 36,
            height: 36,
            borderRadius: 10,
            border: "none",
            background: "#fff",
            boxShadow: "0 1px 2px rgba(24,22,44,0.06)",
            color: "#61616C",
            fontSize: 18,
            cursor: "pointer",
            flexShrink: 0,
          }}
        >
          ‹
        </button>
        <div style={{ flex: 1, height: 6, borderRadius: 999, background: "#E7E6EF", overflow: "hidden" }}>
          <div
            style={{
              height: "100%",
              borderRadius: 999,
              background: PRIMARY,
              width: onboardPct,
              transition: "width .4s ease",
            }}
          />
        </div>
        <span style={{ fontSize: 13, fontWeight: 600, color: "#9797A1", flexShrink: 0 }}>{step + 1} / 2</span>
      </div>

      {step === 0 ? (
        <div className="cf-scroll" style={{ flex: 1, overflowY: "auto", padding: "8px 20px 20px", animation: "cfUp .4s ease both" }}>
          <h1 style={{ fontSize: 24, fontWeight: 800, letterSpacing: "-0.02em", margin: "6px 0 6px", color: "#15141B" }}>
            현재 내 스펙을
            <br />
            알려주세요
          </h1>
          <p style={{ fontSize: 14, color: "#61616C", margin: "0 0 22px", lineHeight: 1.55 }}>
            입력한 정보는 추천과 합격자 비교에만 쓰이고, 언제든 수정할 수 있어요.
          </p>

          <div style={cardStyle}>
            <label style={fieldLabelStyle}>학점 (GPA)</label>
            <div style={{ display: "flex", alignItems: "baseline", gap: 6 }}>
              <input
                value={spec.gpa}
                onChange={(e) => onSetGpa(Number(e.target.value))}
                type="number"
                step="0.01"
                style={{ ...numberInputStyle, width: 92 }}
              />
              <span style={{ fontSize: 17, fontWeight: 700, color: "#9797A1" }}>/ 4.5</span>
            </div>
          </div>

          <div style={cardStyle}>
            <label style={{ ...fieldLabelStyle, marginBottom: 12 }}>어학 성적</label>
            <div style={{ display: "flex", gap: 8, marginBottom: 14 }}>
              {LANG_TYPES.map((lt) => (
                <Chip
                  key={lt}
                  selected={spec.langType === lt}
                  onClick={() => onSetLangType(lt)}
                  style={{ flex: 1, height: 40, borderRadius: 12 }}
                >
                  {lt}
                </Chip>
              ))}
            </div>
            <div style={{ display: "flex", alignItems: "baseline", gap: 6 }}>
              <input
                value={spec.langScore}
                onChange={(e) => onSetLangScore(Number(e.target.value))}
                type="number"
                style={{ ...numberInputStyle, width: 120 }}
              />
              <span style={{ fontSize: 15, fontWeight: 600, color: "#9797A1" }}>점</span>
            </div>
          </div>

          <div style={cardStyle}>
            <label style={{ ...fieldLabelStyle, marginBottom: 4 }}>보유 자격증</label>
            <p style={{ fontSize: 12, color: "#B0B0BA", margin: "0 0 12px" }}>해당하는 항목을 모두 선택하세요</p>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
              {CERT_OPTIONS.map((c) => {
                const selected = spec.certs.includes(c);
                return (
                  <Chip key={c} selected={selected} onClick={() => onToggleCert(c)}>
                    {selected ? "✓ " : ""}
                    {c}
                  </Chip>
                );
              })}
            </div>
          </div>

          <div style={{ ...cardStyle, marginBottom: 0 }}>
            <label style={{ ...fieldLabelStyle, marginBottom: 4 }}>경험 / 활동</label>
            <p style={{ fontSize: 12, color: "#B0B0BA", margin: "0 0 12px" }}>인턴 · 동아리 · 프로젝트 등</p>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
              {EXP_OPTIONS.map((e) => {
                const selected = spec.exps.includes(e);
                return (
                  <Chip key={e} selected={selected} onClick={() => onToggleExp(e)}>
                    {selected ? "✓ " : ""}
                    {e}
                  </Chip>
                );
              })}
            </div>
          </div>
        </div>
      ) : (
        <div className="cf-scroll" style={{ flex: 1, overflowY: "auto", padding: "8px 20px 20px", animation: "cfUp .4s ease both" }}>
          <h1 style={{ fontSize: 24, fontWeight: 800, letterSpacing: "-0.02em", margin: "6px 0 6px", color: "#15141B" }}>
            어떤 목표를
            <br />
            준비하고 있나요?
          </h1>
          <p style={{ fontSize: 14, color: "#61616C", margin: "0 0 22px", lineHeight: 1.55 }}>
            목표에 따라 추천 활동과 비교 대상이 달라져요.
          </p>

          <div style={{ marginBottom: 22 }}>
            <label style={{ display: "block", fontSize: 14, fontWeight: 700, color: "#15141B", marginBottom: 12 }}>
              희망 직무
            </label>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
              {JOB_OPTIONS.map((j) => {
                const st = chipStyle(target.job === j);
                return (
                  <button
                    key={j}
                    type="button"
                    onClick={() => onSetJob(j)}
                    style={{
                      height: 44,
                      padding: "0 18px",
                      borderRadius: 14,
                      fontSize: 14.5,
                      fontWeight: 600,
                      cursor: "pointer",
                      border: `1px solid ${st.borderColor}`,
                      background: st.background,
                      color: st.color,
                      transition: "all .15s ease",
                    }}
                  >
                    {j}
                  </button>
                );
              })}
            </div>
          </div>

          <div style={{ marginBottom: 22 }}>
            <label style={{ display: "block", fontSize: 14, fontWeight: 700, color: "#15141B", marginBottom: 12 }}>
              희망 기업 규모
            </label>
            <div style={{ display: "flex", gap: 8 }}>
              {SIZE_OPTIONS.map(([label, hint]) => {
                const selected = target.size === label;
                const st = chipStyle(selected);
                return (
                  <button
                    key={label}
                    type="button"
                    onClick={() => onSetSize(label)}
                    style={{
                      flex: 1,
                      height: 60,
                      borderRadius: 16,
                      fontSize: 14.5,
                      fontWeight: 700,
                      cursor: "pointer",
                      border: `1px solid ${st.borderColor}`,
                      background: st.background,
                      color: st.color,
                      display: "flex",
                      flexDirection: "column",
                      alignItems: "center",
                      justifyContent: "center",
                      gap: 3,
                      transition: "all .15s ease",
                    }}
                  >
                    {label}
                    <span
                      style={{
                        fontSize: 11,
                        fontWeight: 500,
                        color: selected ? `color-mix(in srgb, ${PRIMARY} 70%, #999)` : "#B0B0BA",
                      }}
                    >
                      {hint}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>

          <div>
            <label style={{ display: "block", fontSize: 14, fontWeight: 700, color: "#15141B", marginBottom: 12 }}>
              관심 업계
            </label>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
              {INDUSTRY_OPTIONS.map((i) => {
                const st = chipStyle(target.industry === i);
                return (
                  <button
                    key={i}
                    type="button"
                    onClick={() => onSetIndustry(i)}
                    style={{
                      height: 44,
                      padding: "0 18px",
                      borderRadius: 14,
                      fontSize: 14.5,
                      fontWeight: 600,
                      cursor: "pointer",
                      border: `1px solid ${st.borderColor}`,
                      background: st.background,
                      color: st.color,
                      transition: "all .15s ease",
                    }}
                  >
                    {i}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      )}

      <div
        style={{
          padding: "14px 20px calc(20px + env(safe-area-inset-bottom))",
          background: "linear-gradient(180deg, rgba(246,246,249,0), #F6F6F9 32%)",
        }}
      >
        <button
          type="button"
          onClick={onNext}
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: 8,
            width: "100%",
            height: 54,
            border: "none",
            borderRadius: 16,
            background: PRIMARY,
            color: "#fff",
            fontSize: 16,
            fontWeight: 700,
            cursor: "pointer",
            boxShadow: `0 8px 20px color-mix(in srgb, ${PRIMARY} 32%, transparent)`,
          }}
        >
          {onboardCta}
        </button>
      </div>
    </div>
  );
}
