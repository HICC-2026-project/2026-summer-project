import { apiFetch, apiFetchRaw } from "@/lib/api";
import type { JobCode, LanguageScorePayload, ReviewStatus } from "@/features/spec-road/types";

// 검수(관리자) API. 서버가 /api/v1/admin/**를 ROLE_ADMIN으로 막으므로
// 일반 사용자는 403(ACCESS_DENIED)을 받는다 — 화면은 그 응답을 그대로 안내로 바꾼다.

export type { ReviewStatus };
export type ReviewAction = "APPROVE" | "REJECT";

export interface AdminPasserReport {
  reportId: string;
  jobType: string;
  jobTypeLabel: string;
  year: number;
  gpa: number | null;
  gpaMax: number | null;
  languageScores: { type?: string; score?: number; maxScore?: number; grade?: string }[];
  certifications: string[];
  experienceCount: number | null;
  status: ReviewStatus;
  proof: { originalName: string | null; contentType: string | null; fileSize: number | null } | null;
  createdAt: string;
  reviewedAt: string | null;
  rejectReason: string | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export function getAdminReports(status: ReviewStatus, page = 0, size = 20): Promise<PageResponse<AdminPasserReport>> {
  const q = new URLSearchParams({ status, page: String(page), size: String(size) });
  return apiFetch<PageResponse<AdminPasserReport>>(`/api/v1/admin/passers/reports?${q}`);
}

export function reviewReport(reportId: string, action: ReviewAction, reason?: string): Promise<AdminPasserReport> {
  return apiFetch<AdminPasserReport>(`/api/v1/admin/passers/reports/${reportId}`, {
    method: "PATCH",
    body: JSON.stringify({ action, reason }),
  });
}

// 증빙 이미지는 <img src>로 바로 못 연다 — Authorization 헤더가 필요하다.
// blob으로 받아 object URL을 만들어 보여준다. 호출자가 revokeObjectURL로 정리한다. 파일이 없으면(404) null.
export async function fetchProofObjectUrl(reportId: string): Promise<string | null> {
  try {
    const res = await apiFetchRaw(`/api/v1/admin/passers/reports/${reportId}/proof`);
    return URL.createObjectURL(await res.blob());
  } catch {
    return null;
  }
}

// 운영 요약 — 검수 대기 수, 직무별 비교 가능 합격자 수, Gemini 사용량/실패율.
export interface OpsSummary {
  pendingReports: number;
  comparablePassersByJob: Record<string, number>;
  minSampleSize: number;
  gemini: {
    usedToday: number;
    dailyLimit: number;
    success: number;
    failure: number;
    failureRate: number;
    avgLatencyMs: number;
    maxLatencyMs: number;
    lastFailureEpochMs: number | null;
  };
}

export function getOpsSummary(): Promise<OpsSummary> {
  return apiFetch<OpsSummary>("/api/v1/admin/ops/summary");
}

// 합격자 수기 등록 — 공개 커뮤니티·블로그 후기를 관리자가 직접 입력한다.
// 사용자 제보와 달리 증빙 파일·GitHub 아이디·데이터 이용 동의가 없고, 대신 출처(sourceNote)가
// 필수다. 저장 즉시 검수 없이 비교 데이터에 반영된다(POST /api/v1/admin/passers).
export interface AdminPasserEntryRequest {
  jobType: JobCode;
  year: number;
  gpa: number;
  gpaMax: number;
  languageScores: LanguageScorePayload[];
  certifications: string[];
  experienceCount: number;
  sourceNote: string;
}

export interface AdminPasserEntryResponse {
  passerId: string;
  status: ReviewStatus;
  message: string;
}

export function createAdminPasserEntry(request: AdminPasserEntryRequest): Promise<AdminPasserEntryResponse> {
  return apiFetch<AdminPasserEntryResponse>("/api/v1/admin/passers", {
    method: "POST",
    body: JSON.stringify(request),
  });
}
