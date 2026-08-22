import { JOB_OPTIONS, LANG_MAX, PRIMARY } from "./data";
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

// "YYYY-MM-DD"를 로컬 자정 기준 Date로 파싱한다.
// ⚠️ new Date("YYYY-MM-DD")는 스펙상 UTC 자정으로 해석되는데, TODAY는 로컬(KST) 자정이라
// 두 값 사이에 +9시간(+0.375일)이 항상 끼어든다. Math.ceil(N + 0.375) = N + 1이므로
// 경계일뿐 아니라 모든 마감일의 D-day가 정확히 하루씩 부풀었다 — 오늘 마감이 "D-1",
// 내일 마감이 "D-2"로 표시돼 사용자가 실제보다 하루 여유가 있다고 믿게 되는 문제.
function parseLocalDate(dateStr: string): Date {
  const [y, m, d] = dateStr.split("-").map(Number);
  return new Date(y, (m || 1) - 1, d || 1);
}

// 기준일(오늘 자정)은 호출할 때마다 새로 계산한다.
// ⚠️ 예전엔 모듈 로드 시점에 한 번 고정되는 상수(TODAY)를 썼는데, SPA 특성상 탭을 새로고침
// 없이 자정 넘겨 계속 열어두면 "오늘"이 어제(또는 며칠 전)에 고정된 채 모든 D-day가
// 조용히 부풀어 보였다 — 마감일 문자열은 맞는데 D-N만 어긋나는 이상한 결론.
function todayMidnight(): number {
  const t = new Date();
  t.setHours(0, 0, 0, 0);
  return t.getTime();
}

export function dday(dateStr: string): string {
  const diff = Math.ceil((parseLocalDate(dateStr).getTime() - todayMidnight()) / 86400000);
  return diff <= 0 ? "마감" : `D-${diff}`;
}

export function ddayColor(dateStr: string): string {
  const diff = Math.ceil((parseLocalDate(dateStr).getTime() - todayMidnight()) / 86400000);
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

// 점수형 시험(TOEIC·TOEFL — LANG_MAX에 만점이 있는 시험)의 "0"은 미입력과 같게 취급한다.
// 실제 최저점도 0이 아니고, 0을 값으로 치면 홈 요약이 "어학 1개", 프로필이 "TOEIC 0"으로
// 잡혀 없는 성적이 있는 것처럼 보인다. 등급형(OPIc)은 비어있지만 않으면 유효하다.
// 숫자가 아닌 쓰레기 입력(Number → NaN)도 여기서 함께 걸러진다.
export function hasMeaningfulLangScore(type: string, value: string): boolean {
  if (!value) return false;
  return LANG_MAX[type] == null ? true : Number(value) > 0;
}

// GET /users/me 응답의 languageScores 배열을 화면 state({ TOEIC: "850" })로 되돌린다.
// 0점 방지 이전에 저장된 옛 데이터에 score 0이 남아 있을 수 있어 여기서도 걸러낸다.
export function fromLanguageScoresPayload(payload: LanguageScorePayload[]): Spec["langScores"] {
  const result: Spec["langScores"] = {};
  for (const item of payload) {
    const type = FRONT_LANGUAGE_TYPE_NAMES[item.type] ?? item.type;
    const value = item.grade ?? (item.score != null ? String(item.score) : "");
    if (hasMeaningfulLangScore(type, value)) result[type] = value;
  }
  return result;
}

// Converts the flat { TOEIC: "850", OPIc: "IH" } state into the
// [{ type, score, maxScore } | { type, grade }] array PUT /users/me/spec expects.
// 0점(및 숫자가 아닌 값)은 미입력으로 보고 요청에서 제외한다 — 백엔드도 @Positive로 거부한다.
export function toLanguageScoresPayload(langScores: Spec["langScores"]): LanguageScorePayload[] {
  return Object.entries(langScores)
    .filter(([type, value]) => hasMeaningfulLangScore(type, value))
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
// (Recommendation에서 optional). 위치·갭 요약은 응답 최상단 specPosition 하나로 온다.
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
    specPosition: res.specPosition ?? null,
    isAiRecommendation: res.aiRecommendation ?? res.isAiRecommendation ?? false,
    targetJobName: res.targetJobName,
    dailyLimitReached: res.dailyLimitReached ?? false,
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
