import { JOB_OPTIONS, LANG_MAX, PRIMARY, TODAY } from "./data";
import type {
  JobCode,
  LanguageScorePayload,
  Priority,
  Recommendation,
  RecommendationMeta,
  RecommendationsResponse,
  RoadmapMilestone,
  RoadmapResponse,
  Spec,
} from "./types";

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

// 직무 코드 → 화면 라벨. 저장은 코드로, 표시는 라벨로 한다.
export function jobLabel(code: JobCode | ""): string {
  return JOB_OPTIONS.find((option) => option.code === code)?.label ?? "";
}

// GET /users/me의 jobType을 화면 값으로 정규화한다.
// 직무 코드 도입 전에 저장된 한글 직무명("SW 개발")은 미선택으로 떨어뜨려 다시 고르게 한다.
export function normalizeJobCode(value: string | null | undefined): JobCode | "" {
  return JOB_OPTIONS.some((option) => option.code === value) ? (value as JobCode) : "";
}

// Frontend language-type keys that differ from the API 명세서's `type` value.
const API_LANGUAGE_TYPE_NAMES: Record<string, string> = {
  OPIc: "OPIC",
};

const FRONT_LANGUAGE_TYPE_NAMES: Record<string, string> = {
  OPIC: "OPIc",
};

// GET /users/me 응답의 languageScores 배열을 화면 state({ TOEIC: "850" })로 되돌린다.
export function fromLanguageScoresPayload(payload: LanguageScorePayload[]): Spec["langScores"] {
  const result: Spec["langScores"] = {};
  for (const item of payload) {
    const type = FRONT_LANGUAGE_TYPE_NAMES[item.type] ?? item.type;
    const value = item.grade ?? (item.score != null ? String(item.score) : "");
    if (value) result[type] = value;
  }
  return result;
}

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

// 백엔드 활동 type 코드(INTERNSHIP 등)를 화면 라벨(인턴십 등)로 바꾼다.
// 매핑에 없는 값은 원본을 그대로 보여준다(신규 type 추가 시에도 화면이 깨지지 않도록).
const ACTIVITY_TYPE_LABELS: Record<string, string> = {
  INTERNSHIP: "인턴십",
  EXTERNAL: "대외활동",
  COMPETITION: "공모전",
  EDUCATION: "교육",
};

export function activityTypeLabel(type: string): string {
  return ACTIVITY_TYPE_LABELS[type] ?? type;
}

// GET /recommendations 응답을 화면 추천 카드 모델로 변환한다.
// 백엔드는 개별 활동 점수·기관·태그·bullets를 주지 않으므로 해당 필드는 비워 둔다
// (Recommendation에서 optional). 점수는 응답 최상단 matchScore 하나로 통일됐다.
export function fromRecommendationsResponse(res: RecommendationsResponse): Recommendation[] {
  return res.activities.map((a) => ({
    id: a.id,
    type: activityTypeLabel(a.type),
    name: a.name,
    reason: a.reason,
    deadline: a.deadline,
  }));
}

export function toRecommendationMeta(res: RecommendationsResponse): RecommendationMeta {
  return {
    matchScore: res.matchScore,
    comparisonMessage: res.comparisonMessage,
    isAiRecommendation: res.isAiRecommendation,
  };
}

// 백엔드 priority 문자열을 화면 Priority로 정규화한다.
// 알 수 없는 값이 와도 화면이 깨지지 않게 MEDIUM으로 떨어뜨린다.
function normalizePriority(priority: string): Priority {
  return priority === "HIGH" || priority === "MEDIUM" || priority === "LOW" ? priority : "MEDIUM";
}

// GET /roadmaps 응답을 화면 로드맵 단계 모델로 변환한다.
// 백엔드는 목업의 phase·current를 주지 않아 비워 두고(optional), matchedActivities는 그대로 전달한다.
export function fromRoadmapResponse(res: RoadmapResponse): RoadmapMilestone[] {
  return res.timeline.map((step) => ({
    period: step.period,
    priority: normalizePriority(step.priority),
    activity: step.activity,
    reason: step.reason,
    matchedActivities: step.matchedActivities,
  }));
}
