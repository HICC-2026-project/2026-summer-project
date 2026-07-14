import type { CompareRow, Recommendation, RoadmapMilestone } from "./types";

export const PRIMARY = "#2F6FED";

export const RECOMMENDATIONS: Recommendation[] = [
  {
    id: 1,
    type: "인턴십",
    name: "삼성 청년 SW 아카데미",
    org: "삼성전자",
    deadline: "2026-07-31",
    score: 92,
    passers: 24,
    reason: "학점 3.8·Java 경험이 최근 합격자 조건과 정확히 일치해요.",
    tags: ["대기업", "SW교육", "수료증"],
    bullets: [
      "학점 3.8은 합격자 평균(3.6)을 넘어 서류 통과 가능성이 높아요.",
      "보유한 정보처리기사가 우대 자격증에 포함돼요.",
      "실무 프로젝트 6개월로 부족한 경험을 한 번에 채울 수 있어요.",
    ],
  },
  {
    id: 2,
    type: "대외활동",
    name: "네이버 부스트캠프 멤버십",
    org: "네이버 커넥트재단",
    deadline: "2026-08-15",
    score: 88,
    passers: 31,
    reason: "CS·알고리즘 중심이라 개발 동아리 경험과 잘 맞아요.",
    tags: ["알고리즘", "CS", "비대면"],
    bullets: [
      "개발 동아리 경험이 지원 동기에서 강점으로 작용해요.",
      "수료 시 네이버 채용 연계 트랙에 지원할 수 있어요.",
      "어학 요건이 없어 지금 스펙으로 바로 지원 가능해요.",
    ],
  },
  {
    id: 3,
    type: "교육",
    name: "카카오 테크 캠퍼스",
    org: "카카오",
    deadline: "2026-08-05",
    score: 86,
    passers: 12,
    reason: "실무 프로젝트 비중이 높아 포트폴리오 강화에 좋아요.",
    tags: ["실무", "포트폴리오", "오프라인"],
    bullets: [
      "프로젝트 산출물을 그대로 포트폴리오로 쓸 수 있어요.",
      "자격증보다 실습 결과물을 중요하게 평가해요.",
      "유사 합격자 12명 중 8명이 3.5 이상 학점이었어요.",
    ],
  },
  {
    id: 4,
    type: "공모전",
    name: "전국 대학생 SW 해커톤",
    org: "과학기술정보통신부",
    deadline: "2026-09-20",
    score: 84,
    passers: 8,
    reason: "수상 이력이 대기업 서류에서 가점 요소가 돼요.",
    tags: ["해커톤", "수상", "팀"],
    bullets: [
      "수상 시 대기업 공채 서류에서 정량 가점을 받아요.",
      "팀 프로젝트 경험으로 협업 역량을 어필할 수 있어요.",
      "2학기 로드맵의 공모전 항목과 시기가 맞아요.",
    ],
  },
  {
    id: 5,
    type: "인턴십",
    name: "토스 NEXT 개발자 인턴",
    org: "비바리퍼블리카",
    deadline: "2026-08-31",
    score: 79,
    passers: 15,
    reason: "정규직 전환 트랙 — 어학 점수를 조금 올리면 더 유리해요.",
    tags: ["전환형", "핀테크", "유급"],
    bullets: [
      "정규직 전환율이 높아 실질적 취업으로 이어져요.",
      "합격자 평균 어학이 890점대라 보완이 필요해요.",
      "실무 코드 리뷰 경험을 쌓기 좋은 환경이에요.",
    ],
  },
  {
    id: 6,
    type: "대외활동",
    name: "오픈소스 컨트리뷰션 아카데미",
    org: "정보통신산업진흥원",
    deadline: "2026-08-10",
    score: 76,
    passers: 5,
    reason: "GitHub 활동 증빙에 도움 — 참여 난이도가 낮아요.",
    tags: ["오픈소스", "멘토링", "온라인"],
    bullets: [
      "GitHub 커밋 이력으로 개발 역량을 증빙할 수 있어요.",
      "멘토가 배정돼 첫 오픈소스 기여도 부담 없어요.",
      "데이터가 아직 적어 AI 일반 추천을 함께 제공해요.",
    ],
  },
];

export const ROADMAP: RoadmapMilestone[] = [
  {
    period: "2026 · 7월",
    phase: "지금 바로",
    priority: "HIGH",
    activity: "정보처리기사 실기 대비",
    reason: "서류 가점 필수 자격증. 접수 마감이 임박했어요.",
    current: true,
  },
  {
    period: "2026 · 7–8월",
    phase: "여름방학",
    priority: "HIGH",
    activity: "SSAFY · 여름 인턴십 지원",
    reason: "가장 부족한 실무 경험을 채우는 핵심 구간이에요.",
    current: false,
  },
  {
    period: "2026 · 9–11월",
    phase: "2학기",
    priority: "MEDIUM",
    activity: "SW 공모전 1개 참가",
    reason: "수상 이력으로 서류 경쟁력을 끌어올려요.",
    current: false,
  },
  {
    period: "2027 · 상반기",
    phase: "내년",
    priority: "LOW",
    activity: "대기업 공채 대비 · 포트폴리오 정리",
    reason: "누적 활동을 서류·면접 스토리로 구성해요.",
    current: false,
  },
];

export const COMPARE_ROWS: CompareRow[] = [
  { label: "학점", weight: "30%", myVal: "3.8", avgVal: "3.6", myPct: 84, avgPct: 80, status: "충족" },
  { label: "어학 (TOEIC)", weight: "25%", myVal: "850", avgVal: "885", myPct: 86, avgPct: 89, status: "부족" },
  { label: "자격증", weight: "20%", myVal: "2개", avgVal: "1.4개", myPct: 67, avgPct: 47, status: "충족" },
  { label: "경험", weight: "25%", myVal: "2건", avgVal: "3.1건", myPct: 40, avgPct: 62, status: "부족" },
];

export const JOB_OPTIONS = ["SW 개발", "데이터 엔지니어", "AI/ML", "기획/PM", "보안"];
export const SIZE_OPTIONS: [string, string][] = [
  ["대기업", "공채 중심"],
  ["중견·중소", "수시 채용"],
  ["스타트업", "실무 즉시"],
];
export const INDUSTRY_OPTIONS = ["IT·플랫폼", "금융", "제조", "게임"];
export const LANG_TYPES = ["TOEIC", "OPIc", "TOEFL"];

// 학교마다 학점 만점 기준(4.5/4.3/4.0)이 달라, 학교명 대신 만점 기준 자체를 직접 고르게 한다.
// (학교명 수집·매핑 없이도 정확한 백분율 환산이 가능해 개인정보 이슈를 원천적으로 피할 수 있다.)
export const GPA_SCALE_OPTIONS = [4.5, 4.3, 4.0];

// TOEIC(LC+RC)·TOEFL(iBT) 만점. OPIc은 점수가 아닌 등급제라 만점 개념이 없다.
export const LANG_MAX: Record<string, number | null> = {
  TOEIC: 990,
  TOEFL: 120,
  OPIc: null,
};

export const OPIC_GRADES = ["NL", "NM", "NH", "IL", "IM1", "IM2", "IM3", "IH", "AL"];

export const TODAY = new Date("2026-07-07");
export const COMPARE_TARGET = "삼성 청년 SW 아카데미";
export const PASSER_COUNT = 24;
export const READINESS = 78;
export const READINESS_RANK = "18%";
export const COMPARE_SCORE = 82;
export const COMPARE_DEG = 295;
export const USER_NAME = "이지우";
