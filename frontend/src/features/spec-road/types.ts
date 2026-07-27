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

export interface Recommendation {
  id: number;
  type: string;
  name: string;
  org: string;
  deadline: string;
  score: number;
  passers: number;
  reason: string;
  tags: string[];
  bullets: string[];
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
