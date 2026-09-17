"use client";

import { useEffect, useState } from "react";
import { ApiError } from "@/lib/api";
import { postExperienceEnrich, postExperienceQuestions } from "../api";
import { DEPTH_LABELS, INK, INK_FAINT, INK_MUTED, PRIMARY } from "../data";
import type { Experience, ExperienceEnrichResult } from "../types";
import { AreaChips } from "./AreaChips";

// 답변 1건당 최대 길이(계약). textarea maxLength로도 막지만, 붙여넣기 등으로 넘어온 값도 잘라낸다.
const MAX_ANSWER_LENGTH = 1000;

const FALLBACK_QUESTIONS_ERROR = "질문을 불러오지 못했어요. 잠시 후 다시 시도해주세요.";
const FALLBACK_ENRICH_ERROR = "분석에 실패했어요. 잠시 후 다시 시도해주세요.";

// 모달의 표시 단계. 컨테이너(ExperienceEnrichModal)가 API 호출 결과에 따라 전이시키고,
// 뷰(ExperienceEnrichModalView)는 이 값만 보고 분기한다(GithubSectionView와 같은 패턴).
type Phase =
  | { kind: "loading" }
  | { kind: "no-questions" }
  | { kind: "questions"; questions: string[] }
  | { kind: "submitting"; questions: string[] }
  | { kind: "result"; result: ExperienceEnrichResult }
  // 응답이 전부 빈 값(areas 없음·depth null·roleSummary null)인 계약상 실패 폴백.
  | { kind: "enrich-failed" }
  // 질문 생성·분석 API가 에러를 던진 경우(한도 초과 등) — ApiError.message를 그대로 보여준다.
  | { kind: "error"; message: string };

const overlayStyle = {
  position: "absolute",
  inset: 0,
  background: "rgba(20,18,40,0.42)",
  zIndex: 50,
  display: "flex",
  alignItems: "flex-end",
} as const;

const sheetStyle = {
  width: "100%",
  maxHeight: "85%",
  overflowY: "auto",
  background: "#fff",
  borderRadius: "26px 26px 0 0",
  padding: "20px 22px calc(20px + env(safe-area-inset-bottom))",
} as const;

const mutedTextStyle = { fontSize: 13, color: INK_MUTED, lineHeight: 1.55, margin: "0 0 16px" } as const;

const primaryButtonStyle = {
  height: 48,
  border: "none",
  borderRadius: 14,
  background: PRIMARY,
  color: "#fff",
  fontSize: 14.5,
  fontWeight: 700,
  cursor: "pointer",
  width: "100%",
} as const;

const secondaryButtonStyle = {
  flex: 1,
  height: 48,
  border: "1px solid #E1E0EA",
  borderRadius: 14,
  background: "#fff",
  color: INK,
  fontSize: 14.5,
  fontWeight: 700,
  cursor: "pointer",
} as const;

const textareaStyle = {
  width: "100%",
  minHeight: 64,
  padding: "10px 12px",
  borderRadius: 12,
  border: "1px solid #E1E0EA",
  fontSize: 13.5,
  lineHeight: 1.5,
  outline: "none",
  resize: "vertical",
  fontFamily: "inherit",
} as const;

const depthBadgeStyle = {
  display: "inline-block",
  fontSize: 11.5,
  fontWeight: 700,
  color: INK_MUTED,
  background: "#fff",
  border: "1px solid #E1E0EA",
  padding: "3px 9px",
  borderRadius: 999,
} as const;

interface ExperienceEnrichModalViewProps {
  phase: Phase;
  answers: string[];
  onAnswerChange: (index: number, value: string) => void;
  onSubmit: () => void;
  onApply: () => void;
  onClose: () => void;
}

// 순수 표시 컴포넌트 — 단계(phase)만 보고 로딩 / 질문 없음 / 질문+답변 입력 / 제출 중 /
// 결과 미리보기 / 실패(폴백·에러) 여섯 분기를 그린다. API 호출·단계 전이는 컨테이너
// (ExperienceEnrichModal)가 맡는다 — CompareTab·GithubSectionView와 같은 컨테이너/뷰 분리.
export function ExperienceEnrichModalView({
  phase,
  answers,
  onAnswerChange,
  onSubmit,
  onApply,
  onClose,
}: ExperienceEnrichModalViewProps) {
  const canSubmit = phase.kind === "questions" && answers.some((a) => a.trim().length > 0);

  return (
    <div style={overlayStyle}>
      <div style={sheetStyle}>
        <div style={{ display: "flex", justifyContent: "center", marginBottom: 10 }}>
          <div style={{ width: 40, height: 5, borderRadius: 999, background: "#E1E0EA" }} />
        </div>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 14 }}>
          <div style={{ fontSize: 16, fontWeight: 800, color: INK }}>AI 깊이 분석</div>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            style={{ border: "none", background: "transparent", color: INK_FAINT, fontSize: 16, cursor: "pointer" }}
          >
            ✕
          </button>
        </div>

        {phase.kind === "loading" && <p style={mutedTextStyle}>질문을 준비하고 있어요...</p>}

        {phase.kind === "no-questions" && (
          <>
            <p style={mutedTextStyle}>지금은 질문을 만들 수 없어요. 잠시 후 다시 시도해 주세요.</p>
            <button type="button" onClick={onClose} style={primaryButtonStyle}>
              닫기
            </button>
          </>
        )}

        {phase.kind === "error" && (
          <>
            <p style={{ ...mutedTextStyle, color: "#E5484D" }}>{phase.message}</p>
            <button type="button" onClick={onClose} style={primaryButtonStyle}>
              닫기
            </button>
          </>
        )}

        {phase.kind === "enrich-failed" && (
          <>
            <p style={{ ...mutedTextStyle, color: "#E5484D" }}>분석에 실패했어요.</p>
            <button type="button" onClick={onClose} style={primaryButtonStyle}>
              닫기
            </button>
          </>
        )}

        {(phase.kind === "questions" || phase.kind === "submitting") && (
          <>
            <p style={mutedTextStyle}>편하게 답할 수 있는 질문에만 답해주세요. (최소 1개, 각 1000자 이내)</p>
            <div style={{ display: "flex", flexDirection: "column", gap: 14, marginBottom: 18 }}>
              {phase.questions.map((q, i) => (
                <div key={i}>
                  <label style={{ display: "block", fontSize: 13, fontWeight: 700, color: INK, marginBottom: 6 }}>
                    {q}
                  </label>
                  <textarea
                    value={answers[i] ?? ""}
                    onChange={(e) => onAnswerChange(i, e.target.value.slice(0, MAX_ANSWER_LENGTH))}
                    maxLength={MAX_ANSWER_LENGTH}
                    rows={3}
                    placeholder="답변 (선택)"
                    disabled={phase.kind === "submitting"}
                    style={textareaStyle}
                  />
                </div>
              ))}
            </div>
            <div style={{ display: "flex", gap: 8 }}>
              <button type="button" onClick={onClose} disabled={phase.kind === "submitting"} style={secondaryButtonStyle}>
                건너뛰기
              </button>
              <button
                type="button"
                onClick={onSubmit}
                disabled={!canSubmit}
                style={{
                  ...primaryButtonStyle,
                  width: "auto",
                  flex: 1.4,
                  opacity: canSubmit ? 1 : 0.6,
                  cursor: canSubmit ? "pointer" : "not-allowed",
                }}
              >
                {phase.kind === "submitting" ? "분석 중..." : "분석하기"}
              </button>
            </div>
          </>
        )}

        {phase.kind === "result" && (
          <>
            <p style={mutedTextStyle}>이렇게 반영할게요</p>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 6, marginBottom: 12 }}>
              <AreaChips areas={phase.result.areas} />
              {phase.result.depth && <span style={depthBadgeStyle}>{DEPTH_LABELS[phase.result.depth]}</span>}
            </div>
            {phase.result.roleSummary && (
              <p style={{ fontSize: 13, color: "#4A4954", lineHeight: 1.5, margin: "0 0 18px" }}>
                {phase.result.roleSummary}
              </p>
            )}
            <div style={{ display: "flex", gap: 8 }}>
              <button type="button" onClick={onClose} style={secondaryButtonStyle}>
                취소
              </button>
              <button type="button" onClick={onApply} style={{ ...primaryButtonStyle, width: "auto", flex: 1.4 }}>
                적용
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

interface ExperienceEnrichModalProps {
  experience: Experience;
  /** 분석 결과를 그 경험에 반영(areas 병합·depth 설정·role 보충)하도록 부모에 알린다. */
  onApply: (result: ExperienceEnrichResult) => void;
  onClose: () => void;
}

// 데이터 요청(질문 생성 → 답변 제출 → 분석)을 담당하는 컨테이너. 렌더링은 뷰에 위임한다.
export function ExperienceEnrichModal({ experience, onApply, onClose }: ExperienceEnrichModalProps) {
  const [phase, setPhase] = useState<Phase>({ kind: "loading" });
  const [answers, setAnswers] = useState<string[]>([]);

  useEffect(() => {
    let cancelled = false;
    postExperienceQuestions(experience)
      .then((res) => {
        if (cancelled) return;
        if (res.questions.length === 0) {
          setPhase({ kind: "no-questions" });
        } else {
          setAnswers(res.questions.map(() => ""));
          setPhase({ kind: "questions", questions: res.questions });
        }
      })
      .catch((error) => {
        if (cancelled) return;
        setPhase({ kind: "error", message: error instanceof ApiError ? error.message : FALLBACK_QUESTIONS_ERROR });
      });
    return () => {
      cancelled = true;
    };
    // 이 모달 인스턴스는 경험 한 건에 대해서만 열리고, 닫히면 언마운트된다 — 마운트 시 한 번만 요청한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function handleAnswerChange(index: number, value: string) {
    setAnswers((prev) => prev.map((a, i) => (i === index ? value : a)));
  }

  async function handleSubmit() {
    if (phase.kind !== "questions") return;
    const { questions } = phase;
    const filledAnswers = questions
      .map((q, i) => ({ question: q, answer: (answers[i] ?? "").trim() }))
      .filter((a) => a.answer.length > 0);
    if (filledAnswers.length === 0) return;

    setPhase({ kind: "submitting", questions });
    try {
      const result = await postExperienceEnrich(experience, filledAnswers);
      const isEmpty = result.areas.length === 0 && result.depth == null && !result.roleSummary;
      setPhase(isEmpty ? { kind: "enrich-failed" } : { kind: "result", result });
    } catch (error) {
      setPhase({ kind: "error", message: error instanceof ApiError ? error.message : FALLBACK_ENRICH_ERROR });
    }
  }

  function handleApply() {
    if (phase.kind !== "result") return;
    onApply(phase.result);
  }

  return (
    <ExperienceEnrichModalView
      phase={phase}
      answers={answers}
      onAnswerChange={handleAnswerChange}
      onSubmit={() => void handleSubmit()}
      onApply={handleApply}
      onClose={onClose}
    />
  );
}
