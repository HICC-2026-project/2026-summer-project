import { LANG_MAX, PRIMARY, TODAY } from "./data";
import type { LanguageScorePayload, Spec } from "./types";

export function dday(dateStr: string): string {
  const diff = Math.ceil((new Date(dateStr).getTime() - TODAY.getTime()) / 86400000);
  return diff <= 0 ? "마감" : `D-${diff}`;
}

export function ddayColor(dateStr: string): string {
  const diff = Math.ceil((new Date(dateStr).getTime() - TODAY.getTime()) / 86400000);
  return diff <= 30 ? "#E5484D" : "#9797A1";
}

export function fmtDate(dateStr: string): string {
  const [, month, day] = dateStr.split("-");
  return `${parseInt(month, 10)}월 ${parseInt(day, 10)}일`;
}

export interface ChipStyle {
  background: string;
  color: string;
  borderColor: string;
}

export function chipStyle(selected: boolean): ChipStyle {
  return selected
    ? { background: `color-mix(in srgb, ${PRIMARY} 10%, #fff)`, color: PRIMARY, borderColor: PRIMARY }
    : { background: "#F6F5FA", color: "#61616C", borderColor: "#EAE9F1" };
}

// Frontend language-type keys that differ from the API 명세서's `type` value.
const API_LANGUAGE_TYPE_NAMES: Record<string, string> = {
  OPIc: "OPIC",
};

// Converts the flat { TOEIC: "850", OPIc: "IH" } state into the
// [{ type, score, maxScore } | { type, grade }] array PUT /users/me/spec expects.
export function toLanguageScoresPayload(langScores: Spec["langScores"]): LanguageScorePayload[] {
  return Object.entries(langScores)
    .filter(([, value]) => value !== "")
    .map(([type, value]) => {
      const apiType = API_LANGUAGE_TYPE_NAMES[type] ?? type;
      const maxScore = LANG_MAX[type] ?? null;
      return maxScore == null ? { type: apiType, grade: value } : { type: apiType, score: Number(value), maxScore };
    });
}
