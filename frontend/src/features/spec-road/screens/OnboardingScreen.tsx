"use client";

import { useState, type CSSProperties } from "react";
import { Chip } from "../components/Chip";
import { ExperienceCard } from "../components/ExperienceCard";
import {
  EXPERIENCE_TYPE_OPTIONS,
  GPA_SCALE_OPTIONS,
  GRADE_OPTIONS,
  INDUSTRY_OPTIONS,
  JOB_OPTIONS,
  LANG_MAX,
  LANG_TYPES,
  OPIC_GRADES,
  PRIMARY,
  SIZE_OPTIONS,
} from "../data";
import { chipStyle, parseStackInput } from "../helpers";
import type { Experience, ExperienceType, JobCode, OnboardStep, Spec, Target } from "../types";

interface OnboardingScreenProps {
  step: OnboardStep;
  spec: Spec;
  target: Target;
  onBack: () => void;
  onNext: () => void;
  onSetGpa: (v: string) => void;
  onSetGpaScale: (v: number) => void;
  onSetGrade: (v: number) => void;
  onSetLangScore: (type: string, v: string) => void;
  onAddCert: (v: string) => void;
  onRemoveCert: (v: string) => void;
  onAddExperience: (experience: Experience) => void;
  onRemoveExperience: (index: number) => void;
  onSetJob: (v: JobCode) => void;
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

// Sanitizes free-typed numeric input: strips invalid characters, keeps at most
// one decimal point, and collapses leading zeros (e.g. "03" -> "3") without
// blocking in-progress input like "" or "0.".
function sanitizeNumericInput(raw: string, allowDecimal: boolean): string {
  let value = raw.replace(allowDecimal ? /[^0-9.]/g : /[^0-9]/g, "");
  if (allowDecimal) {
    const firstDot = value.indexOf(".");
    if (firstDot !== -1) {
      value = value.slice(0, firstDot + 1) + value.slice(firstDot + 1).replace(/\./g, "");
    }
  }
  return value.replace(/^0+(?=\d)/, "");
}

function clampToMax(value: string, max: number): string {
  const num = Number(value);
  return value !== "" && value !== "." && !Number.isNaN(num) && num > max ? String(max) : value;
}

export function OnboardingScreen({
  step,
  spec,
  target,
  onBack,
  onNext,
  onSetGpa,
  onSetGpaScale,
  onSetGrade,
  onSetLangScore,
  onAddCert,
  onRemoveCert,
  onAddExperience,
  onRemoveExperience,
  onSetJob,
  onSetSize,
  onSetIndustry,
}: OnboardingScreenProps) {
  const [certInput, setCertInput] = useState("");
  const [experienceDraft, setExperienceDraft] = useState<{
    type: ExperienceType;
    title: string;
    description: string;
    months: string;
    role: string;
    stackInput: string;
  }>({ type: "INTERNSHIP", title: "", description: "", months: "", role: "", stackInput: "" });
  // 기간·역할·기술은 선택 입력이라 기본은 접어 두고, 필요할 때만 펼친다
  // (경험 입력 UI가 무거워지지 않도록 — E11 1단계).
  const [showExperienceDetail, setShowExperienceDetail] = useState(false);
  const onboardPct = step === 0 ? "50%" : "100%";
  const onboardCta = step === 0 ? "다음" : "분석 시작하기";

  // 백엔드가 필수로 요구하는 값(학점·학년·직무)을 채우기 전엔 진행을 막는다.
  // 그냥 보내면 400과 함께 온보딩이 중단되므로, 화면에서 먼저 안내한다.
  const blockedMessage =
    step === 0
      ? spec.gpa === ""
        ? "학점을 입력해주세요"
        : spec.grade == null
          ? "학년을 선택해주세요"
          : null
      : target.job === ""
        ? "희망 직무를 선택해주세요"
        : null;

  function submitCertInput() {
    const value = certInput.trim();
    if (!value || spec.certs.includes(value)) return;
    onAddCert(value);
    setCertInput("");
  }

  // 제목이 비어있으면 추가하지 않는다(경험은 선택 입력이지만, 추가할 땐 제목이 필수).
  // description·role·months·stack이 비어있으면 필드 자체를 넣지 않는다 — 전부 optional이라
  // 빈 값을 보내는 대신 아예 생략해야 계약(빈 값 = 미기입)과 일치한다.
  function submitExperienceInput() {
    const title = experienceDraft.title.trim();
    if (!title) return;
    const description = experienceDraft.description.trim();
    const role = experienceDraft.role.trim();
    const stack = parseStackInput(experienceDraft.stackInput);
    // months는 1~120 정수만 유효하다(계약) — 범위를 벗어나거나 비어있으면 생략한다.
    const monthsNum = Number(experienceDraft.months);
    const months =
      experienceDraft.months !== "" && Number.isInteger(monthsNum) && monthsNum >= 1 && monthsNum <= 120
        ? monthsNum
        : undefined;
    onAddExperience({
      type: experienceDraft.type,
      title,
      ...(description ? { description } : {}),
      ...(months != null ? { months } : {}),
      ...(role ? { role } : {}),
      ...(stack.length > 0 ? { stack } : {}),
    });
    setExperienceDraft((d) => ({ ...d, title: "", description: "", months: "", role: "", stackInput: "" }));
  }

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
            <label style={fieldLabelStyle}>학년</label>
            <div style={{ display: "flex", gap: 6 }}>
              {GRADE_OPTIONS.map((g) => (
                <Chip
                  key={g}
                  selected={spec.grade === g}
                  onClick={() => onSetGrade(g)}
                  style={{ flex: 1, height: 34, fontSize: 12.5, borderRadius: 10 }}
                >
                  {g}학년
                </Chip>
              ))}
            </div>
          </div>

          <div style={cardStyle}>
            <label style={fieldLabelStyle}>학점 (GPA)</label>
            <div style={{ display: "flex", alignItems: "baseline", gap: 6, marginBottom: 12 }}>
              <input
                value={spec.gpa}
                onChange={(e) => {
                  const sanitized = clampToMax(sanitizeNumericInput(e.target.value, true), spec.gpaScale);
                  onSetGpa(sanitized);
                }}
                type="text"
                inputMode="decimal"
                style={{ ...numberInputStyle, width: 92 }}
              />
              <span style={{ fontSize: 17, fontWeight: 700, color: "#9797A1" }}>/ {spec.gpaScale}</span>
            </div>
            <div style={{ display: "flex", gap: 6 }}>
              {GPA_SCALE_OPTIONS.map((scale) => (
                <Chip
                  key={scale}
                  selected={spec.gpaScale === scale}
                  onClick={() => onSetGpaScale(scale)}
                  style={{ flex: 1, height: 34, fontSize: 12.5, borderRadius: 10 }}
                >
                  {scale} 만점
                </Chip>
              ))}
            </div>
          </div>

          <div style={cardStyle}>
            <label style={{ ...fieldLabelStyle, marginBottom: 12 }}>어학 성적</label>
            <p style={{ fontSize: 12, color: "#B0B0BA", margin: "-2px 0 14px" }}>해당하는 시험에 점수를 입력하세요</p>
            <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
              {LANG_TYPES.map((lt) => {
                const langMax = LANG_MAX[lt] ?? null;
                const score = spec.langScores[lt] ?? "";
                return (
                  <div key={lt}>
                    <span style={{ fontSize: 13, fontWeight: 700, color: "#15141B" }}>{lt}</span>
                    <div style={{ marginTop: 6 }}>
                      {lt === "OPIc" ? (
                        <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
                          {OPIC_GRADES.map((grade) => (
                            <Chip
                              key={grade}
                              selected={score === grade}
                              onClick={() => onSetLangScore(lt, score === grade ? "" : grade)}
                              style={{ minWidth: 52 }}
                            >
                              {grade}
                            </Chip>
                          ))}
                        </div>
                      ) : (
                        <div style={{ display: "flex", alignItems: "baseline", gap: 6 }}>
                          <input
                            value={score}
                            onChange={(e) => {
                              const sanitized = sanitizeNumericInput(e.target.value, false);
                              onSetLangScore(lt, langMax != null ? clampToMax(sanitized, langMax) : sanitized);
                            }}
                            type="text"
                            inputMode="numeric"
                            style={{ ...numberInputStyle, fontSize: 22, width: 100 }}
                          />
                          <span style={{ fontSize: 13, fontWeight: 600, color: "#9797A1" }}>
                            {langMax != null ? `/ ${langMax}점` : "점"}
                          </span>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          <div style={cardStyle}>
            <label style={{ ...fieldLabelStyle, marginBottom: 4 }}>보유 자격증</label>
            <p style={{ fontSize: 12, color: "#B0B0BA", margin: "0 0 12px" }}>자격증 이름을 직접 입력해 추가하세요</p>
            <div style={{ display: "flex", gap: 8, marginBottom: spec.certs.length ? 12 : 0 }}>
              <input
                value={certInput}
                onChange={(e) => setCertInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    submitCertInput();
                  }
                }}
                type="text"
                placeholder="예: 정보처리기사"
                style={{
                  flex: 1,
                  height: 42,
                  padding: "0 14px",
                  borderRadius: 12,
                  border: "1px solid #E1E0EA",
                  fontSize: 14,
                  outline: "none",
                }}
              />
              <button
                type="button"
                onClick={submitCertInput}
                style={{
                  height: 42,
                  padding: "0 18px",
                  borderRadius: 12,
                  border: "none",
                  background: PRIMARY,
                  color: "#fff",
                  fontSize: 14,
                  fontWeight: 700,
                  cursor: "pointer",
                }}
              >
                추가
              </button>
            </div>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
              {spec.certs.map((c) => (
                <Chip key={c} selected onClick={() => onRemoveCert(c)}>
                  {c} ✕
                </Chip>
              ))}
            </div>
          </div>

          <div style={{ ...cardStyle, marginBottom: 0 }}>
            <label style={{ ...fieldLabelStyle, marginBottom: 4 }}>경험</label>
            <p style={{ fontSize: 12, color: "#B0B0BA", margin: "0 0 12px" }}>
              인턴·프로젝트 등 경험을 추가하세요 (선택)
            </p>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 6, marginBottom: 10 }}>
              {EXPERIENCE_TYPE_OPTIONS.map(({ code, label }) => (
                <Chip
                  key={code}
                  selected={experienceDraft.type === code}
                  onClick={() => setExperienceDraft((d) => ({ ...d, type: code }))}
                  style={{ height: 34, fontSize: 12.5 }}
                >
                  {label}
                </Chip>
              ))}
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 10 }}>
              <input
                value={experienceDraft.title}
                onChange={(e) => setExperienceDraft((d) => ({ ...d, title: e.target.value }))}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    submitExperienceInput();
                  }
                }}
                type="text"
                placeholder="경험 제목 (예: OO 서비스 백엔드 인턴)"
                style={{
                  height: 42,
                  padding: "0 14px",
                  borderRadius: 12,
                  border: "1px solid #E1E0EA",
                  fontSize: 14,
                  outline: "none",
                }}
              />
              <input
                value={experienceDraft.description}
                onChange={(e) => setExperienceDraft((d) => ({ ...d, description: e.target.value }))}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    submitExperienceInput();
                  }
                }}
                type="text"
                placeholder="간단한 설명 (선택)"
                style={{
                  height: 42,
                  padding: "0 14px",
                  borderRadius: 12,
                  border: "1px solid #E1E0EA",
                  fontSize: 14,
                  outline: "none",
                }}
              />
              <button
                type="button"
                onClick={() => setShowExperienceDetail((v) => !v)}
                style={{
                  alignSelf: "flex-start",
                  border: "none",
                  background: "transparent",
                  color: PRIMARY,
                  fontSize: 12.5,
                  fontWeight: 700,
                  cursor: "pointer",
                  padding: "2px 0",
                }}
              >
                {showExperienceDetail ? "상세 입력 접기 ▲" : "상세 입력 (기간·역할·기술) ▼"}
              </button>
              {showExperienceDetail && (
                <>
                  <div style={{ display: "flex", alignItems: "baseline", gap: 6 }}>
                    <input
                      value={experienceDraft.months}
                      onChange={(e) => {
                        const sanitized = clampToMax(sanitizeNumericInput(e.target.value, false), 120);
                        setExperienceDraft((d) => ({ ...d, months: sanitized }));
                      }}
                      type="text"
                      inputMode="numeric"
                      placeholder="활동 기간 (선택)"
                      style={{
                        flex: 1,
                        height: 42,
                        padding: "0 14px",
                        borderRadius: 12,
                        border: "1px solid #E1E0EA",
                        fontSize: 14,
                        outline: "none",
                      }}
                    />
                    <span style={{ fontSize: 13, fontWeight: 600, color: "#9797A1", flexShrink: 0 }}>개월</span>
                  </div>
                  <input
                    value={experienceDraft.role}
                    onChange={(e) => setExperienceDraft((d) => ({ ...d, role: e.target.value }))}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") {
                        e.preventDefault();
                        submitExperienceInput();
                      }
                    }}
                    type="text"
                    maxLength={100}
                    placeholder="역할 한 줄 (선택, 예: 백엔드 API 설계·구현)"
                    style={{
                      height: 42,
                      padding: "0 14px",
                      borderRadius: 12,
                      border: "1px solid #E1E0EA",
                      fontSize: 14,
                      outline: "none",
                    }}
                  />
                  <input
                    value={experienceDraft.stackInput}
                    onChange={(e) => setExperienceDraft((d) => ({ ...d, stackInput: e.target.value }))}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") {
                        e.preventDefault();
                        submitExperienceInput();
                      }
                    }}
                    type="text"
                    placeholder="사용 기술 (콤마로 구분, 선택, 예: Spring Boot, JPA, MySQL)"
                    style={{
                      height: 42,
                      padding: "0 14px",
                      borderRadius: 12,
                      border: "1px solid #E1E0EA",
                      fontSize: 14,
                      outline: "none",
                    }}
                  />
                </>
              )}
            </div>
            <button
              type="button"
              onClick={submitExperienceInput}
              style={{
                width: "100%",
                height: 42,
                borderRadius: 12,
                border: "none",
                background: PRIMARY,
                color: "#fff",
                fontSize: 14,
                fontWeight: 700,
                cursor: "pointer",
                marginBottom: spec.experiences.length ? 12 : 0,
              }}
            >
              추가
            </button>
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {spec.experiences.map((exp, idx) => (
                <div
                  key={idx}
                  style={{
                    display: "flex",
                    alignItems: "flex-start",
                    justifyContent: "space-between",
                    gap: 10,
                    padding: "10px 12px",
                    borderRadius: 12,
                    background: "#F6F5FA",
                    border: "1px solid #EAE9F1",
                  }}
                >
                  <ExperienceCard experience={exp} />
                  <button
                    type="button"
                    onClick={() => onRemoveExperience(idx)}
                    style={{
                      border: "none",
                      background: "transparent",
                      color: "#9797A1",
                      fontSize: 14,
                      cursor: "pointer",
                      flexShrink: 0,
                    }}
                  >
                    ✕
                  </button>
                </div>
              ))}
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
              {JOB_OPTIONS.map(({ code, label }) => {
                const st = chipStyle(target.job === code);
                return (
                  <button
                    key={code}
                    type="button"
                    onClick={() => onSetJob(code)}
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
                    {label}
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
        {blockedMessage && (
          <p style={{ margin: "0 0 10px", fontSize: 13, fontWeight: 600, color: "#9797A1", textAlign: "center" }}>
            {blockedMessage}
          </p>
        )}
        <button
          type="button"
          onClick={onNext}
          disabled={blockedMessage != null}
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: 8,
            width: "100%",
            height: 54,
            border: "none",
            borderRadius: 16,
            background: blockedMessage ? "#D9D8E4" : PRIMARY,
            color: "#fff",
            fontSize: 16,
            fontWeight: 700,
            cursor: blockedMessage ? "not-allowed" : "pointer",
            boxShadow: blockedMessage ? "none" : `0 8px 20px color-mix(in srgb, ${PRIMARY} 32%, transparent)`,
            transition: "background .15s ease",
          }}
        >
          {onboardCta}
        </button>
      </div>
    </div>
  );
}
