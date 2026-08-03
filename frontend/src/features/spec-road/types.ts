export type Screen = "login" | "intro" | "onboard" | "analyzing" | "app";
export type OnboardStep = 0 | 1;
export type Tab = "home" | "roadmap" | "compare" | "profile";
export type Priority = "HIGH" | "MEDIUM" | "LOW";

// 백엔드 TargetJobRequest가 @Pattern으로 허용하는 직무 코드.
// 화면에는 한글 라벨을 보여주고 저장할 때는 이 코드를 보낸다.
export type JobCode = "BACKEND" | "FRONTEND" | "DATA_ENGINEER" | "AI_ML" | "PM" | "SECURITY";

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

// job은 미선택 상태("")를 허용한다. 저장 전에 반드시 코드를 고르게 하고,
// 예전에 한글 직무명("SW 개발")으로 저장된 값을 불러올 때도 미선택으로 떨어뜨린다.
export interface Target {
  job: JobCode | "";
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

// 화면에서 쓰는 로드맵 단계 모델.
// period·priority·activity·reason은 백엔드 GET /roadmaps가 항상 주는 값이고,
// phase·current는 목업(둘러보기)에만 있는 값이라 optional로 둔다.
// matchedActivities는 백엔드가 RAG로 검증해 붙여주는 실제 DB 활동 목록.
export interface RoadmapMilestone {
  period: string;
  priority: Priority;
  activity: string;
  reason: string;
  phase?: string;
  current?: boolean;
  matchedActivities?: MatchedActivity[];
}

// GET /api/v1/roadmaps 단계에 매칭된 실제 DB 대외활동 (이름·마감일·지원 링크 모두 DB 원본).
export interface MatchedActivity {
  activityId: string;
  name: string;
  type: string;
  organization: string;
  deadline: string;
  url: string;
}

// GET /api/v1/roadmaps 타임라인 단계 (실제 컨트롤러/DTO 기준).
// 백엔드는 목업의 phase·current를 주지 않고, 대신 matchedActivities를 준다.
export interface RoadmapStep {
  period: string;
  priority: string;
  activity: string;
  reason: string;
  matchedActivities: MatchedActivity[];
}

// GET /api/v1/roadmaps 전체 응답.
// 명세서의 /roadmap · targetJob 필드와 달리 실제 컨트롤러는 /roadmaps이고 timeline만 반환한다.
export interface RoadmapResponse {
  timeline: RoadmapStep[];
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
