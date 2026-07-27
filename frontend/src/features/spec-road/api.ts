import { apiFetch } from "@/lib/api";
import { toLanguageScoresPayload } from "./helpers";
import type {
  RecommendationsResponse,
  RoadmapResponse,
  Spec,
  Target,
  TargetJobResponse,
  UserMeResponse,
  UserSpecResponse,
} from "./types";

export function getMe(): Promise<UserMeResponse> {
  return apiFetch<UserMeResponse>("/api/v1/users/me");
}

// F-03 맞춤 활동 추천. JWT 필수 — apiFetch가 저장된 토큰을 Authorization 헤더로 붙인다.
// 응답은 24시간 캐싱되며, isAiRecommendation이 false면 기본 추천(fallback)이다.
export function getRecommendations(): Promise<RecommendationsResponse> {
  return apiFetch<RecommendationsResponse>("/api/v1/recommendations");
}

// F-05 커리어 로드맵. 실제 컨트롤러 경로는 /roadmaps (명세서의 /roadmap과 다름 — 실제 기준).
// 각 단계에는 RAG로 검증된 실제 DB 활동(matchedActivities)이 포함된다.
export function getRoadmap(): Promise<RoadmapResponse> {
  return apiFetch<RoadmapResponse>("/api/v1/roadmaps");
}

// 화면 state → 백엔드 필드명(gpaMax, certifications)으로 변환해 저장한다.
// 어학·자격증이 없으면 null이 아니라 빈 배열을 보낸다 (BE-2 계약).
export function putSpec(spec: Spec): Promise<UserSpecResponse> {
  return apiFetch<UserSpecResponse>("/api/v1/users/me/spec", {
    method: "PUT",
    body: JSON.stringify({
      gpa: spec.gpa === "" ? null : Number(spec.gpa),
      gpaMax: spec.gpaScale,
      grade: spec.grade,
      languageScores: toLanguageScoresPayload(spec.langScores),
      certifications: spec.certs,
    }),
  });
}

export function putTarget(target: Target): Promise<TargetJobResponse> {
  return apiFetch<TargetJobResponse>("/api/v1/users/me/target", {
    method: "PUT",
    body: JSON.stringify({
      jobType: target.job,
      companySize: target.size,
      industry: target.industry,
    }),
  });
}
