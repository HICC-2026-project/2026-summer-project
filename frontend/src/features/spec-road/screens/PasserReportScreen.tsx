"use client";

import { useState, type CSSProperties, type FormEvent } from "react";
import { Chip } from "../components/Chip";
import { GPA_SCALE_OPTIONS, JOB_OPTIONS, LANG_MAX, LANG_TYPES, OPIC_GRADES, PRIMARY } from "../data";
import { toLanguageScoresPayload } from "../helpers";
import type { JobCode, PasserReportRequest, PasserReportResponse, Spec } from "../types";

interface PasserReportScreenProps {
  initialSpec: Spec;
  initialJob: JobCode | "";
  onBack: () => void;
  onSubmit: (request: PasserReportRequest) => Promise<PasserReportResponse>;
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

const inputStyle: CSSProperties = {
  flex: 1,
  minWidth: 0,
  height: 44,
  padding: "0 14px",
  borderRadius: 12,
  border: "1px solid #E1E0EA",
  background: "#fff",
  color: "#15141B",
  fontSize: 14,
  outline: "none",
};

const scoreInputStyle: CSSProperties = {
  border: "none",
  borderBottom: `2px solid ${PRIMARY}`,
  background: "transparent",
  fontSize: 22,
  fontWeight: 800,
  color: "#15141B",
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

export function PasserReportScreen({ initialSpec, initialJob, onBack, onSubmit }: PasserReportScreenProps) {
  const [jobType, setJobType] = useState<JobCode | "">(initialJob);
  const [year, setYear] = useState(() => String(new Date().getFullYear()));
  const [gpa, setGpa] = useState(initialSpec.gpa);
  const [gpaMax, setGpaMax] = useState(initialSpec.gpaScale);
  const [langScores, setLangScores] = useState<Record<string, string>>(() => ({ ...initialSpec.langScores }));
  const [certifications, setCertifications] = useState<string[]>(() => [...initialSpec.certs]);
  const [certInput, setCertInput] = useState("");
  const [experienceCount, setExperienceCount] = useState("0");
  const [consent, setConsent] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [result, setResult] = useState<PasserReportResponse | null>(null);

  const numericYear = Number(year);
  const numericGpa = Number(gpa);
  const numericExperienceCount = Number(experienceCount);
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
                : !consent
                  ? "익명 데이터 활용에 동의해주세요."
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

  async function submitReport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (validationMessage || jobType === "") return;

    setSubmitting(true);
    setSubmitError(null);
    try {
      const response = await onSubmit({
        jobType,
        year: numericYear,
        gpa: numericGpa,
        gpaMax,
        languageScores: toLanguageScoresPayload(langScores),
        certifications,
        experienceCount: numericExperienceCount,
        consent,
      });
      setResult(response);
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : "제보 접수에 실패했습니다. 잠시 후 다시 시도해주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form
      onSubmit={submitReport}
      style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", background: "#F6F6F9" }}
    >
      <header style={{ padding: "18px 20px 12px", display: "flex", alignItems: "center", gap: 14 }}>
        <button
          type="button"
          onClick={onBack}
          disabled={submitting}
          aria-label="프로필로 돌아가기"
          style={{
            width: 36,
            height: 36,
            borderRadius: 10,
            border: "none",
            background: "#fff",
            boxShadow: "0 1px 2px rgba(24,22,44,0.06)",
            color: "#61616C",
            fontSize: 18,
            cursor: submitting ? "not-allowed" : "pointer",
            flexShrink: 0,
          }}
        >
          ‹
        </button>
        <div>
          <div style={{ fontSize: 17, fontWeight: 800, color: "#15141B", letterSpacing: "-0.02em" }}>
            합격자 스펙 제보
          </div>
          <div style={{ marginTop: 2, fontSize: 11.5, color: "#9797A1" }}>검수 완료 후 비교 데이터에 반영돼요</div>
        </div>
      </header>

      <div className="cf-scroll" style={{ flex: 1, overflowY: "auto", padding: "8px 20px 24px", animation: "cfUp .35s ease both" }}>
        <h1 style={{ margin: "6px 0 7px", fontSize: 24, fontWeight: 800, color: "#15141B", letterSpacing: "-0.02em" }}>
          합격 당시 스펙을
          <br />
          알려주세요
        </h1>
        <p style={{ margin: "0 0 22px", fontSize: 14, color: "#61616C", lineHeight: 1.55 }}>
          이름과 연락처는 저장하지 않으며, 입력한 스펙만 익명 통계에 활용합니다.
        </p>

        <section style={cardStyle} aria-labelledby="report-job-label">
          <div id="report-job-label" style={fieldLabelStyle}>합격 직무</div>
          <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
            {JOB_OPTIONS.map(({ code, label }) => (
              <Chip key={code} selected={jobType === code} onClick={() => setJobType(code)}>
                {label}
              </Chip>
            ))}
          </div>
        </section>

        <section style={cardStyle}>
          <label htmlFor="report-year" style={fieldLabelStyle}>합격 연도</label>
          <div style={{ display: "flex", alignItems: "baseline", gap: 7 }}>
            <input
              id="report-year"
              value={year}
              onChange={(event) => setYear(sanitizeNumericInput(event.target.value, false).slice(0, 4))}
              type="text"
              inputMode="numeric"
              maxLength={4}
              style={{ ...scoreInputStyle, width: 92 }}
            />
            <span style={{ fontSize: 14, fontWeight: 600, color: "#9797A1" }}>년</span>
          </div>
        </section>

        <section style={cardStyle}>
          <label htmlFor="report-gpa" style={fieldLabelStyle}>합격 당시 학점</label>
          <div style={{ display: "flex", alignItems: "baseline", gap: 7, marginBottom: 13 }}>
            <input
              id="report-gpa"
              value={gpa}
              onChange={(event) => setGpa(clampToMax(sanitizeNumericInput(event.target.value, true), gpaMax))}
              type="text"
              inputMode="decimal"
              style={{ ...scoreInputStyle, width: 92 }}
            />
            <span style={{ fontSize: 15, fontWeight: 700, color: "#9797A1" }}>/ {gpaMax}</span>
          </div>
          <div style={{ display: "flex", gap: 6 }}>
            {GPA_SCALE_OPTIONS.map((scale) => (
              <Chip
                key={scale}
                selected={gpaMax === scale}
                onClick={() => {
                  setGpaMax(scale);
                  setGpa((value) => clampToMax(value, scale));
                }}
                style={{ flex: 1, height: 34, padding: 0, borderRadius: 10, fontSize: 12.5 }}
              >
                {scale} 만점
              </Chip>
            ))}
          </div>
        </section>

        <section style={cardStyle} aria-labelledby="report-language-label">
          <div id="report-language-label" style={{ ...fieldLabelStyle, marginBottom: 4 }}>어학 성적</div>
          <p style={{ margin: "0 0 14px", fontSize: 12, color: "#B0B0BA" }}>없으면 입력하지 않아도 됩니다.</p>
          <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            {LANG_TYPES.map((type) => {
              const maxScore = LANG_MAX[type] ?? null;
              const value = langScores[type] ?? "";
              return (
                <div key={type}>
                  <span style={{ fontSize: 13, fontWeight: 700, color: "#15141B" }}>{type}</span>
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
                        <span style={{ fontSize: 13, fontWeight: 600, color: "#9797A1" }}>/ {maxScore}점</span>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </section>

        <section style={cardStyle}>
          <label htmlFor="report-certification" style={{ ...fieldLabelStyle, marginBottom: 4 }}>보유 자격증</label>
          <p style={{ margin: "0 0 12px", fontSize: 12, color: "#B0B0BA" }}>없으면 추가하지 않아도 됩니다.</p>
          <div style={{ display: "flex", gap: 8, marginBottom: certifications.length ? 12 : 0 }}>
            <input
              id="report-certification"
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
          <label htmlFor="report-experience" style={{ ...fieldLabelStyle, marginBottom: 4 }}>관련 경험 수</label>
          <p style={{ margin: "0 0 12px", fontSize: 12, color: "#B0B0BA", lineHeight: 1.5 }}>
            인턴, 프로젝트, 공모전, 대외활동 등 직무 관련 경험을 합산해주세요.
          </p>
          <div style={{ display: "flex", alignItems: "baseline", gap: 7 }}>
            <input
              id="report-experience"
              value={experienceCount}
              onChange={(event) => setExperienceCount(sanitizeNumericInput(event.target.value, false).slice(0, 3))}
              type="text"
              inputMode="numeric"
              style={{ ...scoreInputStyle, width: 82 }}
            />
            <span style={{ fontSize: 14, fontWeight: 600, color: "#9797A1" }}>건</span>
          </div>
        </section>

        <label
          style={{
            display: "flex",
            alignItems: "flex-start",
            gap: 11,
            padding: "15px 16px",
            borderRadius: 16,
            border: `1px solid ${consent ? `color-mix(in srgb, ${PRIMARY} 32%, #E1E0EA)` : "#E1E0EA"}`,
            background: consent ? `color-mix(in srgb, ${PRIMARY} 5%, #fff)` : "#fff",
            cursor: "pointer",
          }}
        >
          <input
            checked={consent}
            onChange={(event) => setConsent(event.target.checked)}
            type="checkbox"
            style={{ width: 18, height: 18, margin: "1px 0 0", accentColor: PRIMARY, flexShrink: 0 }}
          />
          <span style={{ fontSize: 13, color: "#4A4954", lineHeight: 1.55 }}>
            제보한 스펙이 익명화되어 합격자 비교 및 추천 데이터로 활용되는 것에 동의합니다.
          </span>
        </label>
      </div>

      <footer style={{ padding: "12px 20px calc(20px + env(safe-area-inset-bottom))", background: "#F6F6F9" }}>
        {(submitError || validationMessage) && (
          <p
            aria-live="polite"
            style={{ margin: "0 0 10px", fontSize: 12.5, fontWeight: 600, color: submitError ? "#E5484D" : "#9797A1", textAlign: "center" }}
          >
            {submitError ?? validationMessage}
          </p>
        )}
        <button
          type="submit"
          disabled={submitting || validationMessage != null}
          style={{
            width: "100%",
            height: 54,
            border: "none",
            borderRadius: 16,
            background: submitting || validationMessage ? "#D9D8E4" : PRIMARY,
            color: "#fff",
            fontSize: 16,
            fontWeight: 700,
            cursor: submitting || validationMessage ? "not-allowed" : "pointer",
            boxShadow: submitting || validationMessage ? "none" : `0 8px 20px color-mix(in srgb, ${PRIMARY} 30%, transparent)`,
          }}
        >
          {submitting ? "접수 중..." : "익명으로 제보하기"}
        </button>
      </footer>

      {result && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="passer-report-success-title"
          style={{
            position: "absolute",
            inset: 0,
            zIndex: 20,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: 24,
            background: "rgba(21,20,27,0.48)",
            backdropFilter: "blur(4px)",
          }}
        >
          <div style={{ width: "100%", maxWidth: 360, padding: "28px 24px 22px", borderRadius: 24, background: "#fff", textAlign: "center" }}>
            <div
              aria-hidden="true"
              style={{
                width: 52,
                height: 52,
                margin: "0 auto 15px",
                borderRadius: 18,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                background: `color-mix(in srgb, ${PRIMARY} 10%, #fff)`,
                color: PRIMARY,
                fontSize: 24,
                fontWeight: 800,
              }}
            >
              ✓
            </div>
            <h2 id="passer-report-success-title" style={{ margin: "0 0 9px", fontSize: 20, color: "#15141B", fontWeight: 800 }}>
              제보가 접수되었습니다
            </h2>
            <p style={{ margin: "0 0 21px", fontSize: 13.5, color: "#61616C", lineHeight: 1.6 }}>
              관리자 검수 완료 후 추천 및 합격자 비교 데이터에 반영됩니다.
            </p>
            <button
              type="button"
              onClick={onBack}
              style={{ width: "100%", height: 48, border: "none", borderRadius: 14, background: PRIMARY, color: "#fff", fontSize: 15, fontWeight: 700, cursor: "pointer" }}
            >
              프로필로 돌아가기
            </button>
          </div>
        </div>
      )}
    </form>
  );
}
