import { apiFetch } from "@/lib/api";
import { getRefreshToken } from "@/lib/auth";
import { toLanguageScoresPayload } from "./helpers";
import type {
  ActivityDetailResponse,
  Experience,
  ExperienceEnrichResult,
  ExperienceQuestionAnswer,
  ExperienceQuestionsResponse,
  GithubAnalysisAccepted,
  GithubProfile,
  MyPasserReport,
  PasserReportRequest,
  PasserReportResponse,
  ReactionType,
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

// E10-2(F-09) — 활동에 대한 반응 등록/변경(upsert). 즉시 추천을 재생성하지 않고
// 다음 추천 갱신 때 반영된다(7/14 "유저당 1건 + 24시간 캐시" 결정 유지 — 백엔드 주석 참고).
export function postRecommendationFeedback(activityId: string, reaction: ReactionType): Promise<void> {
  return apiFetch<void>(`/api/v1/recommendations/${activityId}/feedback`, {
    method: "POST",
    body: JSON.stringify({ reaction }),
  });
}

// 같은 반응 버튼을 다시 누르면 해제한다.
export function deleteRecommendationFeedback(activityId: string): Promise<void> {
  return apiFetch<void>(`/api/v1/recommendations/${activityId}/feedback`, { method: "DELETE" });
}

// F-05 커리어 로드맵. 실제 컨트롤러 경로는 /roadmaps (명세서의 /roadmap과 다름 — 실제 기준).
// 각 단계에는 RAG로 검증된 실제 DB 활동(matchedActivities)이 포함된다.
export function getRoadmap(): Promise<RoadmapResponse> {
  return apiFetch<RoadmapResponse>("/api/v1/roadmaps");
}

// 화면 state → 백엔드 필드명(gpaMax, certifications)으로 변환해 저장한다.
// 어학·자격증·경험이 없으면 null이 아니라 빈 배열을 보낸다 (BE-2 계약).
export function putSpec(spec: Spec): Promise<UserSpecResponse> {
  return apiFetch<UserSpecResponse>("/api/v1/users/me/spec", {
    method: "PUT",
    body: JSON.stringify({
      gpa: spec.gpa === "" ? null : Number(spec.gpa),
      gpaMax: spec.gpaScale,
      grade: spec.grade,
      languageScores: toLanguageScoresPayload(spec.langScores),
      certifications: spec.certs,
      experiences: spec.experiences,
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

// 내 제보 목록(최신순). 검수 전(PENDING)인지 반영됐는지(VERIFIED) 확인용.
export function getMyPasserReports(): Promise<MyPasserReport[]> {
  return apiFetch<MyPasserReport[]>("/api/v1/passers/reports/me");
}

// 회원 탈퇴. 서버가 스펙·목표·추천·로드맵·리프레시 토큰을 함께 지운다(제보한 합격자 데이터는 익명으로 남음).
export function deleteMe(): Promise<void> {
  return apiFetch<void>("/api/v1/users/me", { method: "DELETE" });
}

// 닉네임 변경. 앱에서 바꾼 닉네임은 이후 카카오 로그인이 덮어쓰지 않는다(서버 nickname_overridden).
export function patchNickname(nickname: string): Promise<UserMeResponse> {
  return apiFetch<UserMeResponse>("/api/v1/users/me/nickname", {
    method: "PATCH",
    body: JSON.stringify({ nickname }),
  });
}

// E3 1단계: GitHub 공개 레포 분석. 미등록이면 connected:false, 등록됐으면 진행 상태·결과를 받는다.
export function getGithubProfile(): Promise<GithubProfile> {
  return apiFetch<GithubProfile>("/api/v1/users/me/github");
}

// 분석 시작(최초) 또는 재분석. 202로 PENDING만 돌아오고, 실제 결과는 폴링으로 받는다.
// 진행 중이거나 24시간 재분석 쿨다운이면 409 — 호출부가 ApiError.message를 그대로 보여준다.
export function postGithubAnalysis(url: string): Promise<GithubAnalysisAccepted> {
  return apiFetch<GithubAnalysisAccepted>("/api/v1/users/me/github", {
    method: "POST",
    body: JSON.stringify({ url }),
  });
}

// 연결 해제. 서버가 분석 결과와 GitHub 파생 경험(Experience.source === "GITHUB")을 함께 지운다.
export function deleteGithubProfile(): Promise<void> {
  return apiFetch<void>("/api/v1/users/me/github", { method: "DELETE" });
}

// 경험 필드 중 질문 생성·심층 분석 계약이 받는 부분집합만 추려 보낸다.
// (months·depth·source 등은 계약에 없어 보내지 않는다 — JSON.stringify가 undefined 필드는 알아서 생략한다)
function toExperienceEnrichInput(experience: Experience) {
  return {
    type: experience.type,
    title: experience.title,
    description: experience.description,
    role: experience.role,
    stack: experience.stack,
    areas: experience.areas,
  };
}

// E11 2차: 경험 심층 질문 생성. 0개면 화면이 "지금은 질문을 만들 수 없어요"로 처리하고,
// 한도 초과 등은 ApiError.message를 그대로 보여준다.
export function postExperienceQuestions(experience: Experience): Promise<ExperienceQuestionsResponse> {
  return apiFetch<ExperienceQuestionsResponse>("/api/v1/users/me/experiences/questions", {
    method: "POST",
    body: JSON.stringify({ experience: toExperienceEnrichInput(experience) }),
  });
}

// E11 2차: 질문 답변을 바탕으로 경험의 areas·depth·역할 요약을 분석한다.
export function postExperienceEnrich(
  experience: Experience,
  answers: ExperienceQuestionAnswer[],
): Promise<ExperienceEnrichResult> {
  return apiFetch<ExperienceEnrichResult>("/api/v1/users/me/experiences/enrich", {
    method: "POST",
    body: JSON.stringify({ experience: toExperienceEnrichInput(experience), answers }),
  });
}
