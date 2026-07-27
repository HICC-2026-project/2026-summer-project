import { apiFetch } from "@/lib/api";
import { toLanguageScoresPayload } from "./helpers";
import type { Spec, Target, TargetJobResponse, UserMeResponse, UserSpecResponse } from "./types";

export function getMe(): Promise<UserMeResponse> {
  return apiFetch<UserMeResponse>("/api/v1/users/me");
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
