import { apiFetch } from "@/lib/api";
import { getRefreshToken } from "@/lib/auth";
import { toLanguageScoresPayload } from "./helpers";
import type {
  ActivityDetailResponse,
  PasserReportRequest,
  PasserReportResponse,
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

// 로그아웃 시 서버의 리프레시 토큰까지 폐기한다.
// 로컬 토큰만 지우면 폐기되지 않은 리프레시 토큰이 서버에 남는다.
export function postLogout(): Promise<void> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return Promise.resolve();

  return apiFetch<void>("/api/v1/auth/logout", {
    method: "POST",
    body: JSON.stringify({ refreshToken }),
  });
}

// 활동 상세. 추천 응답에는 지원 링크가 없어, 상세 시트를 열 때 이걸로 보강한다.
export function getActivity(activityId: string): Promise<ActivityDetailResponse> {
  return apiFetch<ActivityDetailResponse>(`/api/v1/activities/${activityId}`);
}

// F-03 맞춤 활동 추천. JWT 필수 — apiFetch가 저장된 토큰을 Authorization 헤더로 붙인다.
// 스펙을 바꾸지 않으면 저장된 결과를 그대로 돌려주고, 바꾸면 하루 3회까지 새로 생성한다.
// isAiRecommendation이 false면 기본 추천(fallback)이다.
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

// 로그인 사용자의 익명 합격 스펙 제보.
// 접수된 데이터는 팀 검수 전까지 추천·비교에 사용되지 않는다.
export function postPasserReport(request: PasserReportRequest, proof: File): Promise<PasserReportResponse> {
  const formData = new FormData();
  formData.append(
    "request",
    new Blob([JSON.stringify(request)], { type: "application/json" }),
  );
  formData.append("proof", proof);

  return apiFetch<PasserReportResponse>("/api/v1/passers/reports", {
    method: "POST",
    body: formData,
  });
}
