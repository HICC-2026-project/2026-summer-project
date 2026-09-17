export type Screen = "login" | "intro" | "onboard" | "analyzing" | "app" | "passer-report";
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
  experiences: Experience[];
}

// 경험(인턴·프로젝트 등) 한 건. PUT /users/me/spec의 experiences, GET /users/me의
// spec.experiences와 정확히 같은 모양이어야 한다(백엔드와 병행 개발 중인 계약).
export type ExperienceType = "INTERNSHIP" | "PROJECT" | "COMPETITION" | "EXTERNAL" | "EDUCATION" | "ETC";

// 자동 태그(area) 코드. AREA_LABELS(data.ts)의 키 전체를 나열한다 — 라벨 맵을
// 빠짐없이 채우기 위한 용도이고, 실제 필드 타입은 GithubJobCode처럼 string[]로 둔다
// (신규 코드가 늘어나도 화면이 깨지지 않도록).
export type AreaCode =
  | "AUTH"
  | "API"
  | "DB"
  | "CI_CD"
  | "TEST"
  | "UI"
  | "STATE_MGMT"
  | "DATA_PIPELINE"
  | "ML_MODEL"
  | "INFRA"
  | "DOCS"
  | "SECURITY"
  | "PLANNING";

// 경험 깊이 — 2차에서 AI가 채운다(E11 1차는 스키마·표시만 준비). 값이 없으면 미분석 상태다.
export type ExperienceDepth = "IMPLEMENTED" | "CONFIGURED" | "BOILERPLATE";

export interface Experience {
  type: ExperienceType;
  title: string;
  description?: string;
  // 이 경험이 어떻게 추가됐는지. 미기재(undefined)면 수동 입력(MANUAL)과 같다 —
  // GET /users/me · PUT /users/me/spec 왕복에서 그대로 보존해야 한다(E3 1단계 계약).
  source?: "MANUAL" | "GITHUB";
  // 아래 5개는 E11 1단계에서 추가된 선택 필드(깊이 신호). 백엔드와 병행 개발 중인 계약이라
  // 임의로 모양을 바꾸지 않는다.
  months?: number; // 활동 개월수 (1~120)
  role?: string; // 역할 한 줄 (≤100자)
  stack?: string[]; // 사용 기술 (최대 10개, 각 ≤50자)
  // 자동 태그 전용 — 사용자가 직접 입력하지 않는다. 신규 코드에도 화면이 깨지지 않도록
  // AreaCode로 제한하지 않고 string[]로 둔다(GithubJobCode와 같은 이유).
  areas?: string[];
  depth?: ExperienceDepth; // 2차에서 AI가 채운다 — 1차는 표시만 준비.
}

// Shape expected by PUT /users/me/spec's languageScores field (API 명세서 기준).
// Score-based tests (TOEIC, TOEFL) send score+maxScore; grade-based tests (OPIC) send grade only.
export interface LanguageScorePayload {
  type: string;
  score?: number;
  maxScore?: number;
  grade?: string;
}

// POST /api/v1/passers/reports 요청·응답.
// 제보는 검수 전 USER_REPORT / isVerified=false로 저장되며 PENDING 상태를 돌려준다.
export interface PasserReportRequest {
  jobType: JobCode;
  year: number;
  gpa: number;
  gpaMax: number;
  languageScores: LanguageScorePayload[];
  certifications: string[];
  experienceCount: number;
  consent: boolean;
}

export interface PasserReportResponse {
  reportId: string;
  status: ReviewStatus;
  message: string;
}

// GET /passers/reports/me — 내가 제보한 합격자 데이터의 검수 상태.
// 합격자 제보 검수 상태 — 백엔드 domain.ReviewStatus와 같은 세 값.
export type ReviewStatus = "PENDING" | "VERIFIED" | "REJECTED";

export interface MyPasserReport {
  reportId: string;
  jobType: string;
  jobTypeLabel: string;
  year: number;
  status: ReviewStatus;
  createdAt: string;
}

// GET /users/me response shape.
export interface UserSpecResponse {
  gpa: number | null;
  gpaMax: number | null;
  grade: number | null;
  languageScores: LanguageScorePayload[] | null;
  certifications: string[] | null;
  experiences?: Experience[] | null;
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
  role?: "USER" | "ADMIN";
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
// (백엔드는 개별 활동 점수를 주지 않는다 — 위치·갭 요약은 RecommendationMeta.specPosition 참고)
export interface Recommendation {
  id: string | number;
  type: string;
  name: string;
  deadline: string;
  reason: string;
  // 이 활동이 메우는 갭(비교 탭의 갭 이름과 동일). 없으면 undefined.
  targetGap?: string | null;
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
  targetGap?: string | null;
}

// specPosition의 축 하나 — 합격자 분포 내 내 위치.
// percentile이 null이면 "미입력"이다(최하위 0과 다른 상태 — 백엔드가 의도적으로 구분).
export interface AxisPosition {
  axis: "GPA" | "LANGUAGE" | "CERTIFICATION" | "EXPERIENCE" | string;
  label: string;
  myValue: string;
  medianValue: string;
  percentile: number | null;
  coverage: number;
}

// 갭: 이 직무 합격자 다수가 보유하지만 나에게 없는 자격증. 보유율 내림차순으로 온다.
export interface SpecGap {
  name: string;
  holderRatePercent: number;
}

// E11 2차: specPosition의 영역 커버리지 — 목표 직무가 요구하는 영역별 보유 여부.
// 합격자 표본·점수(axes/gaps)와는 별개 신호라 비교 탭에서 독립된 카드로 보여준다.
export interface AreaCoverageItem {
  area: string;
  label: string;
  covered: boolean;
}

// GET /api/v1/recommendations의 specPosition — 합격자 분포 내 위치·갭 계산 결과.
// 예전의 matchScore(가중 총점)·compareRows(충족/부족 행)·comparisonMessage·
// similarPasserCount·unrecognizedCertifications를 전부 이 객체가 대체한다.
export interface SpecPosition {
  // JOB(목표 직무 프로필) | OVERALL(전체 합격자 폴백) | NONE(데이터 부족)
  basis: "JOB" | "OVERALL" | "NONE" | string;
  basisMessage: string;
  sampleSize: number;
  // 목표 직무 코드·라벨(미설정이면 null)과 그 직무의 실제 합격자 수.
  // basis가 OVERALL/NONE일 때 "백엔드 합격자 1명 (3명부터 비교 가능)"으로 부족한 정도를
  // 보여주고 제보를 유도한다 — basisMessage를 파싱하지 않도록 백엔드가 명시 필드로 준다.
  targetJobType?: string | null;
  targetJobLabel?: string | null;
  jobSampleSize?: number;
  minSampleSize?: number;
  demoDataIncluded?: boolean;
  axes: AxisPosition[];
  gaps: SpecGap[];
  matchedCertifications?: string[];
  unmatchedCertifications?: string[];
  // 목표 직무 미설정이면 null. 옛 캐시 응답엔 필드 자체가 없을 수 있어(undefined) 화면에서
  // undefined/null을 모두 "섹션 숨김"으로 같이 취급한다.
  areaCoverage?: AreaCoverageItem[] | null;
}

// GET /api/v1/recommendations 전체 응답.
//
// AI 추천 여부 플래그는 두 이름을 모두 받는다.
// 백엔드 DTO 필드는 isAiRecommendation이지만 Jackson이 boolean의 is 접두사를 떼고
// aiRecommendation으로 직렬화한다(명세서와 실제 응답이 다른 지점).
// 백엔드가 @JsonProperty로 이름을 되돌려도 화면이 깨지지 않도록 둘 다 optional로 둔다.
export interface RecommendationsResponse {
  activities: ApiRecommendationItem[];
  specPosition?: SpecPosition;
  targetJobName?: string;
  aiRecommendation?: boolean;
  isAiRecommendation?: boolean;
  // 스펙이 바뀌었지만 하루 갱신 한도(3회)에 막혀 캐시된 활동 목록을 반환한 경우 true.
  // 위치·갭은 새 스펙 기준으로 재계산돼 오지만 활동 목록은 어제 것일 수 있다.
  dailyLimitReached?: boolean;
}

// 추천 목록과 함께 화면 상단에 표시할 요약 정보(응답 최상단 필드에서 추출).
export interface RecommendationMeta {
  specPosition: SpecPosition | null;
  isAiRecommendation: boolean;
  targetJobName?: string;
  dailyLimitReached: boolean;
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
  // 하루 갱신 한도(3회)에 막혀 이전 로드맵을 그대로 반환한 경우 true.
  dailyLimitReached?: boolean;
}

// GET /api/v1/activities/{id} 응답. 추천 카드에는 없는 지원 링크·주최·태그를 담고 있어,
// 상세 시트를 열 때 불러와 보강한다.
export interface ActivityDetailResponse {
  id: string;
  type: string;
  name: string;
  organization: string | null;
  description: string | null;
  deadline: string;
  tags: string[] | null;
  url: string | null;
}

// GET /api/v1/users/me/github의 status. PENDING(분석 중)·DONE(완료)·
// FAILED(분석 실패)·RATE_LIMITED(GitHub API 호출 한도 초과)의 네 값.
export type GithubAnalysisStatus = "PENDING" | "DONE" | "FAILED" | "RATE_LIMITED";

// GET 응답 jobRatios · repos의 primaryJob은 JobCode 대부분과 겹치지만 "OTHER"(기타)도
// 올 수 있어 유니온에 string을 더해 미래에 늘어날 값에도 화면이 깨지지 않게 한다.
export type GithubJobCode = JobCode | "OTHER" | string;

export interface GithubJobRatio {
  jobType: GithubJobCode;
  label: string;
  ratio: number;
}

export interface GithubRepoSummary {
  name: string;
  primaryJob: GithubJobCode;
  primaryJobLabel: string;
  commits: number;
  firstCommitAt: string;
  lastCommitAt: string;
  mainLanguage: string;
  // E11 1단계: 이 레포에서 감지된 자동 태그(area). areas와 같은 이유로 string[]로 둔다.
  areas?: string[];
}

// GET /api/v1/users/me/github — 미연결(connected:false) 또는 등록된 분석 상태.
export type GithubProfile =
  | { connected: false }
  | {
      connected: true;
      username: string;
      status: GithubAnalysisStatus;
      failureReason: string | null;
      analyzedAt: string | null;
      commitTotal: number | null;
      activeMonths: number | null;
      jobRatios: GithubJobRatio[] | null;
      targetJobMatchRatio: number | null;
      repos: GithubRepoSummary[] | null;
    };

// POST /api/v1/users/me/github 202 응답.
export interface GithubAnalysisAccepted {
  username: string;
  status: "PENDING";
}

// E11 2차: 경험 심층 질문·분석. 계약(백엔드와 병행 개발 중이라 임의로 모양을 바꾸지 않는다):
// POST .../experiences/questions body { experience } → { questions } (0개면 "질문을 만들 수 없다" 처리)
// POST .../experiences/enrich    body { experience, answers } → ExperienceEnrichResult

// 두 엔드포인트가 공통으로 받는 경험 필드 — Experience 전체가 아니라 이 부분집합만 보낸다.
export type ExperienceEnrichInput = Pick<Experience, "type" | "title" | "description" | "role" | "stack" | "areas">;

export interface ExperienceQuestionsResponse {
  questions: string[];
}

export interface ExperienceQuestionAnswer {
  question: string;
  // 화면에서 ≤1000자로 강제한다(계약).
  answer: string;
}

// 전부 빈 값(areas 없음·depth null·roleSummary null)이면 분석 실패로 취급한다(계약) —
// 화면은 이 경우 "분석에 실패했어요"를 보여주고 적용할 것이 없다.
export interface ExperienceEnrichResult {
  areas: string[];
  depth: ExperienceDepth | null;
  roleSummary: string | null;
}
