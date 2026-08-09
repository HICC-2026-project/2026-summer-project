import type { Recommendation, RecommendationMeta, RoadmapMilestone } from "./types";

// 마지막으로 받은 추천·로드맵을 브라우저에 보관한다.
// 다시 로그인했을 때 서버 응답을 기다리는 동안 빈 화면이나 로딩 문구를 보여주는 대신
// 직전 결과를 즉시 띄우고, 뒤에서 새 응답이 오면 조용히 바꿔 끼운다.
const CACHE_KEY = "specroad_last_result";

interface CachedResult {
  userId: string;
  recommendations: Recommendation[];
  recMeta: RecommendationMeta | null;
  roadmap: RoadmapMilestone[];
}

export function saveLastResult(result: CachedResult) {
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify(result));
  } catch {
    // 저장 공간이 없거나 차단된 환경이면 캐시 없이 동작한다.
  }
}

// 다른 계정의 결과가 보이지 않도록 userId가 일치할 때만 돌려준다.
export function loadLastResult(userId: string): CachedResult | null {
  try {
    const raw = localStorage.getItem(CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as CachedResult;
    return parsed.userId === userId ? parsed : null;
  } catch {
    return null;
  }
}

export function clearLastResult() {
  try {
    localStorage.removeItem(CACHE_KEY);
  } catch {
    // 무시
  }
}
