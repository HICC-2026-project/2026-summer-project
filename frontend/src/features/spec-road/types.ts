export type Screen = "login" | "onboard" | "analyzing" | "app";
export type OnboardStep = 0 | 1;
export type Tab = "home" | "roadmap" | "compare" | "profile";
export type Priority = "HIGH" | "MEDIUM" | "LOW";

export interface Spec {
  gpa: string;
  gpaScale: number;
  grade: number | null; // 학년 (1~4)
  langScores: Record<string, string>;
  certs: string[];
}

// Shape expected by PUT /users/me/spec's languageScores field (API 명세서 기준).
// Score-based tests (TOEIC, TOEFL) send score+maxScore; grade-based tests (OPIC) send grade only.
export interface LanguageScorePayload {
  type: string;
  score?: number;
  maxScore?: number;
  grade?: string;
}

// GET /users/me response shape.
export interface UserSpecResponse {
  gpa: number | null;
  gpaMax: number | null;
  grade: number | null;
  languageScores: LanguageScorePayload[] | null;
  certifications: string[] | null;
}

export interface TargetJobResponse {
  jobType: string;
  companySize: string;
  industry: string;
}

export interface UserMeResponse {
  id: string;
  email: string | null;
  nickname: string | null;
  provider: string;
  spec: UserSpecResponse | null;
  target: TargetJobResponse | null;
}

export interface Target {
  job: string;
  size: string;
  industry: string;
}

// 화면에서 쓰는 추천 카드 모델.
// id·type·name·reason·deadline은 백엔드 GET /recommendations가 항상 주는 값이고,
// org·score·passers·tags·bullets는 목업(둘러보기)에만 있는 값이라 optional로 둔다.
// (백엔드는 개별 활동 점수 대신 응답 최상단 matchScore 하나만 반환 — RecommendationMeta 참고)
export interface Recommendation {
  id: string | number;
  type: string;
  name: string;
  deadline: string;
  reason: string;
  org?: string;
  score?: number;
  passers?: number;
  tags?: string[];
  bullets?: string[];
}

// GET /api/v1/recommendations 응답의 개별 활동 (실제 컨트롤러/DTO 기준).
// id는 UUID 문자열, type은 INTERNSHIP | EXTERNAL | COMPETITION | EDUCATION, deadline은 ISO 날짜 문자열.
export interface ApiRecommendationItem {
  id: string;
  type: string;
  name: string;
  reason: string;
  deadline: string;
}

// GET /api/v1/recommendations 전체 응답.
// matchScore는 개별 활동이 아닌 응답 최상단의 단일 점수(0~100),
// isAiRecommendation이 false면 Fallback(일반 추천)이라 화면에서 배지로 안내한다.
export interface RecommendationsResponse {
  activities: ApiRecommendationItem[];
  matchScore: number;
  comparisonMessage: string;
  isAiRecommendation: boolean;
}

// 추천 목록과 함께 화면 상단에 표시할 요약 정보(응답 최상단 필드에서 추출).
export interface RecommendationMeta {
  matchScore: number;
  comparisonMessage: string;
  isAiRecommendation: boolean;
}

export interface RoadmapMilestone {
  period: string;
  phase: string;
  priority: Priority;
  activity: string;
  reason: string;
  current: boolean;
}

export interface CompareRow {
  label: string;
  weight: string;
  myVal: string;
  avgVal: string;
  myPct: number;
  avgPct: number;
  status: "충족" | "부족";
}
