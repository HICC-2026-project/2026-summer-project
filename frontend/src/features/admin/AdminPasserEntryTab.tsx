"use client";

import { useState, type CSSProperties, type FormEvent } from "react";
import { ApiError } from "@/lib/api";
import { Chip } from "@/features/spec-road/components/Chip";
import {
  BADGE,
  GPA_SCALE_OPTIONS,
  INK,
  INK_FAINT,
  INK_MUTED,
  JOB_OPTIONS,
  LANG_MAX,
  LANG_TYPES,
  LINE,
  OPIC_GRADES,
  PRIMARY,
} from "@/features/spec-road/data";
import { toLanguageScoresPayload } from "@/features/spec-road/helpers";
import type { JobCode } from "@/features/spec-road/types";
import { createAdminPasserEntry } from "./api";

// 관리자 합격자 수기 등록 탭 — 공개된 커뮤니티·블로그 합격 후기를 보고 관리자가 직접
// 합격자 스펙을 입력한다(합격자 표본 부족 해소용). 사용자 제보(PasserReportScreen)와
// 입력 구성은 최대한 같게 두되, 증빙 파일·GitHub·데이터 이용 동의는 받지 않고 대신
// 출처(sourceNote)를 필수로 받는다 — 서버가 저장 즉시 검수 없이 반영한다(POST /admin/passers).

const cardStyle: CSSProperties = {
  background: "#fff",
  border: `1px solid ${LINE}`,
  borderRadius: 16,
  padding: 18,
  marginBottom: 12,
};

const fieldLabelStyle: CSSProperties = {
  display: "block",
  fontSize: 13,
  fontWeight: 700,
  color: INK_MUTED,
  marginBottom: 10,
};

const inputStyle: CSSProperties = {
  flex: 1,
  minWidth: 0,
  height: 44,
  padding: "0 14px",
  borderRadius: 12,
  border: "1px solid #E1E0EA",
  background: "#fff",
  color: INK,
  fontSize: 14,
  outline: "none",
  boxSizing: "border-box",
};

const scoreInputStyle: CSSProperties = {
  border: "none",
  borderBottom: `2px solid ${PRIMARY}`,
  background: "transparent",
  fontSize: 20,
  fontWeight: 800,
  color: INK,
  padding: "2px 0",
  outline: "none",
};

function sanitizeNumericInput(raw: string, allowDecimal: boolean, maxFractionDigits = 2): string {
  let value = raw.replace(allowDecimal ? /[^0-9.]/g : /[^0-9]/g, "");
  if (allowDecimal) {
    const firstDot = value.indexOf(".");
    if (firstDot !== -1) {
      const whole = value.slice(0, firstDot);
      const fraction = value.slice(firstDot + 1).replace(/\./g, "").slice(0, maxFractionDigits);
      value = `${whole}.${fraction}`;
    }
  }
  return value.replace(/^0+(?=\d)/, "");
}

function clampToMax(value: string, max: number): string {
  const number = Number(value);
  return value !== "" && value !== "." && Number.isFinite(number) && number > max ? String(max) : value;
}

const emptySpec = {
  jobType: "" as JobCode | "",
  year: String(new Date().getFullYear()),
  gpa: "",
  gpaMax: 4.5,
  langScores: {} as Record<string, string>,
  certifications: [] as string[],
  experienceCount: "0",
  sourceNote: "",
};

export function AdminPasserEntryTab() {
  const [jobType, setJobType] = useState<JobCode | "">(emptySpec.jobType);
  const [year, setYear] = useState(emptySpec.year);
  const [gpa, setGpa] = useState(emptySpec.gpa);
  const [gpaMax, setGpaMax] = useState(emptySpec.gpaMax);
  const [langScores, setLangScores] = useState<Record<string, string>>({ ...emptySpec.langScores });
  const [certifications, setCertifications] = useState<string[]>([]);
  const [certInput, setCertInput] = useState("");
  const [experienceCount, setExperienceCount] = useState(emptySpec.experienceCount);
  const [sourceNote, setSourceNote] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const numericYear = Number(year);
  const numericGpa = Number(gpa);
  const numericExperienceCount = Number(experienceCount);
  const trimmedSourceNote = sourceNote.trim();

  const validationMessage =
    jobType === ""
      ? "합격 직무를 선택해주세요."
      : !/^\d{4}$/.test(year) || numericYear < 2000 || numericYear > 2100
        ? "합격 연도를 2000~2100 사이로 입력해주세요."
        : gpa === "" || gpa === "." || !Number.isFinite(numericGpa)
          ? "합격 당시 학점을 입력해주세요."
          : numericGpa < 0 || numericGpa > gpaMax
            ? `학점은 0~${gpaMax} 사이여야 합니다.`
            : experienceCount === "" || !Number.isInteger(numericExperienceCount)
              ? "관련 경험 수를 입력해주세요."
              : numericExperienceCount < 0 || numericExperienceCount > 100
                ? "관련 경험 수는 0~100 사이여야 합니다."
                : trimmedSourceNote === ""
                  ? "출처(URL 또는 메모)를 입력해주세요."
                  : trimmedSourceNote.length > 500
                    ? "출처는 500자 이하로 입력해주세요."
                    : null;

  function updateLanguageScore(type: string, value: string) {
    setLangScores((scores) => ({ ...scores, [type]: value }));
  }

  function addCertification() {
    const value = certInput.trim();
    if (!value || value.length > 100 || certifications.includes(value) || certifications.length >= 30) return;
    setCertifications((items) => [...items, value]);
    setCertInput("");
  }

  function resetForm() {
    setJobType(emptySpec.jobType);
    setYear(emptySpec.year);
    setGpa(emptySpec.gpa);
    setGpaMax(emptySpec.gpaMax);
    setLangScores({});
    setCertifications([]);
    setCertInput("");
    setExperienceCount(emptySpec.experienceCount);
    setSourceNote("");
  }

  async function submitEntry(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (validationMessage || jobType === "") return;

    setSubmitting(true);
    setSubmitError(null);
    setSuccessMessage(null);
    try {
      const response = await createAdminPasserEntry({
        jobType,
        year: numericYear,
        gpa: numericGpa,
        gpaMax,
        languageScores: toLanguageScoresPayload(langScores),
        certifications,
        experienceCount: numericExperienceCount,
        sourceNote: trimmedSourceNote,
      });
      resetForm();
      setSuccessMessage(response.message);
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "등록에 실패했습니다. 잠시 후 다시 시도해주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={submitEntry} style={{ maxWidth: 640 }}>
      <div
        style={{
          display: "flex",
          gap: 10,
          alignItems: "flex-start",
          padding: "14px 16px",
          borderRadius: 14,
          border: `1px solid ${BADGE.warn.color}`,
          background: BADGE.warn.bg,
          marginBottom: 16,
          fontSize: 12.5,
          color: "#5C4419",
          lineHeight: 1.55,
        }}
      >
        <span aria-hidden="true">⚠️</span>
        <span>
          공개된 합격 후기(커뮤니티·블로그 등)만 입력해주세요. 이름·학교·연락처 등 개인 식별 정보는 절대
          입력하지 마세요. 저장 즉시 검수 없이 비교 데이터에 반영됩니다.
        </span>
      </div>

      {successMessage && (
        <div
          role="status"
          style={{
            padding: "12px 16px",
            borderRadius: 12,
            border: `1px solid ${BADGE.ok.color}`,
            background: BADGE.ok.bg,
            color: BADGE.ok.color,
            fontSize: 13,
            fontWeight: 700,
            marginBottom: 16,
          }}
        >
          {successMessage}
        </div>
      )}

      <section style={cardStyle} aria-labelledby="entry-job-label">
        <div id="entry-job-label" style={fieldLabelStyle}>합격 직무</div>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
          {JOB_OPTIONS.map(({ code, label }) => (
            <Chip key={code} selected={jobType === code} onClick={() => setJobType(code)}>
              {label}
            </Chip>
          ))}
        </div>
      </section>

      <section style={cardStyle}>
        <label htmlFor="entry-year" style={fieldLabelStyle}>합격 연도</label>
        <div style={{ display: "flex", alignItems: "baseline", gap: 7 }}>
          <input
            id="entry-year"
            value={year}
            onChange={(event) => setYear(sanitizeNumericInput(event.target.value, false).slice(0, 4))}
            type="text"
            inputMode="numeric"
            maxLength={4}
            style={{ ...scoreInputStyle, width: 92 }}
          />
          <span style={{ fontSize: 14, fontWeight: 600, color: INK_FAINT }}>년</span>
        </div>
      </section>

      <section style={cardStyle}>
        <label htmlFor="entry-gpa" style={fieldLabelStyle}>합격 당시 학점</label>
        <div style={{ display: "flex", alignItems: "baseline", gap: 7, marginBottom: 13 }}>
          <input
            id="entry-gpa"
            value={gpa}
            onChange={(event) => setGpa(clampToMax(sanitizeNumericInput(event.target.value, true), gpaMax))}
            type="text"
            inputMode="decimal"
            style={{ ...scoreInputStyle, width: 92 }}
          />
          <span style={{ fontSize: 15, fontWeight: 700, color: INK_FAINT }}>/ {gpaMax}</span>
        </div>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {GPA_SCALE_OPTIONS.map((scale) => (
            <Chip
              key={scale}
              selected={gpaMax === scale}
              onClick={() => {
                setGpaMax(scale);
                setGpa((value) => clampToMax(value, scale));
              }}
              style={{ flex: "1 1 auto", minWidth: 84, height: 34, padding: 0, borderRadius: 10, fontSize: 12.5 }}
            >
              {scale} 만점
            </Chip>
          ))}
        </div>
      </section>

      <section style={cardStyle} aria-labelledby="entry-language-label">
        <div id="entry-language-label" style={{ ...fieldLabelStyle, marginBottom: 4 }}>어학 성적</div>
        <p style={{ margin: "0 0 14px", fontSize: 12, color: "#B0B0BA" }}>없으면 입력하지 않아도 됩니다.</p>
        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          {LANG_TYPES.map((type) => {
            const maxScore = LANG_MAX[type] ?? null;
            const value = langScores[type] ?? "";
            return (
              <div key={type}>
                <span style={{ fontSize: 13, fontWeight: 700, color: INK }}>{type}</span>
                <div style={{ marginTop: 7 }}>
                  {type === "OPIc" ? (
                    <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
                      {OPIC_GRADES.map((grade) => (
                        <Chip
                          key={grade}
                          selected={value === grade}
                          onClick={() => updateLanguageScore(type, value === grade ? "" : grade)}
                          style={{ minWidth: 52 }}
                        >
                          {grade}
                        </Chip>
                      ))}
                    </div>
                  ) : (
                    <div style={{ display: "flex", alignItems: "baseline", gap: 7 }}>
                      <input
                        aria-label={`${type} 점수`}
                        value={value}
                        onChange={(event) => {
                          const sanitized = sanitizeNumericInput(event.target.value, false);
                          updateLanguageScore(type, maxScore == null ? sanitized : clampToMax(sanitized, maxScore));
                        }}
                        type="text"
                        inputMode="numeric"
                        style={{ ...scoreInputStyle, width: 100 }}
                      />
                      <span style={{ fontSize: 13, fontWeight: 600, color: INK_FAINT }}>/ {maxScore}점</span>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </section>

      <section style={cardStyle}>
        <label htmlFor="entry-certification" style={{ ...fieldLabelStyle, marginBottom: 4 }}>보유 자격증</label>
        <p style={{ margin: "0 0 12px", fontSize: 12, color: "#B0B0BA" }}>없으면 추가하지 않아도 됩니다.</p>
        <div style={{ display: "flex", gap: 8, marginBottom: certifications.length ? 12 : 0 }}>
          <input
            id="entry-certification"
            value={certInput}
            onChange={(event) => setCertInput(event.target.value.slice(0, 100))}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.preventDefault();
                addCertification();
              }
            }}
            type="text"
            maxLength={100}
            placeholder="예: 정보처리기사"
            style={inputStyle}
          />
          <button
            type="button"
            onClick={addCertification}
            disabled={!certInput.trim() || certifications.length >= 30}
            style={{
              height: 44,
              padding: "0 17px",
              border: "none",
              borderRadius: 12,
              background: PRIMARY,
              color: "#fff",
              fontSize: 14,
              fontWeight: 700,
              cursor: "pointer",
              flexShrink: 0,
            }}
          >
            추가
          </button>
        </div>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
          {certifications.map((certification) => (
            <Chip
              key={certification}
              selected
              onClick={() => setCertifications((items) => items.filter((item) => item !== certification))}
            >
              {certification} ✕
            </Chip>
          ))}
        </div>
      </section>

      <section style={cardStyle}>
        <label htmlFor="entry-experience" style={{ ...fieldLabelStyle, marginBottom: 4 }}>관련 경험 수</label>
        <p style={{ margin: "0 0 12px", fontSize: 12, color: "#B0B0BA", lineHeight: 1.5 }}>
          인턴, 프로젝트, 공모전, 대외활동 등 직무 관련 경험을 합산해주세요.
        </p>
        <div style={{ display: "flex", alignItems: "baseline", gap: 7 }}>
          <input
            id="entry-experience"
            value={experienceCount}
            onChange={(event) => setExperienceCount(sanitizeNumericInput(event.target.value, false).slice(0, 3))}
            type="text"
            inputMode="numeric"
            style={{ ...scoreInputStyle, width: 82 }}
          />
          <span style={{ fontSize: 14, fontWeight: 600, color: INK_FAINT }}>건</span>
        </div>
      </section>

      <section style={cardStyle}>
        <label htmlFor="entry-source-note" style={{ ...fieldLabelStyle, marginBottom: 4 }}>
          출처 <span style={{ color: BADGE.bad.color }}>*</span>
        </label>
        <p style={{ margin: "0 0 12px", fontSize: 12, color: "#B0B0BA", lineHeight: 1.5 }}>
          이 스펙을 확인한 공개 후기의 URL 또는 메모를 남겨주세요. 검수 없이 바로 반영되는 만큼 출처 확인이
          필요해요.
        </p>
        <textarea
          id="entry-source-note"
          value={sourceNote}
          onChange={(event) => setSourceNote(event.target.value.slice(0, 500))}
          placeholder="예: https://blog.example.com/2026-backend-passed"
          maxLength={500}
          rows={3}
          style={{ width: "100%", boxSizing: "border-box", border: "1px solid #E1E0EA", borderRadius: 10, padding: 10, fontSize: 13, resize: "vertical" }}
        />
        <div style={{ marginTop: 6, textAlign: "right", fontSize: 11, color: INK_FAINT }}>{sourceNote.length}/500</div>
      </section>

      {(submitError || validationMessage) && (
        <p
          aria-live="polite"
          style={{ margin: "0 0 12px", fontSize: 12.5, fontWeight: 600, color: submitError ? BADGE.bad.color : INK_FAINT }}
        >
          {submitError ?? validationMessage}
        </p>
      )}

      <button
        type="submit"
        disabled={submitting || validationMessage != null}
        style={{
          width: "100%",
          height: 50,
          border: "none",
          borderRadius: 14,
          background: submitting || validationMessage ? "#D9D8E4" : PRIMARY,
          color: "#fff",
          fontSize: 15,
          fontWeight: 700,
          cursor: submitting || validationMessage ? "not-allowed" : "pointer",
        }}
      >
        {submitting ? "등록 중…" : "합격자 데이터 등록"}
      </button>
    </form>
  );
}
