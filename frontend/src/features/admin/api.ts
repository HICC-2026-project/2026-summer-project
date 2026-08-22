import { API_BASE_URL, apiFetch } from "@/lib/api";
import { getAccessToken } from "@/lib/auth";

// 검수(관리자) API. 서버가 /api/v1/admin/**를 ROLE_ADMIN으로 막으므로
// 일반 사용자는 403(ACCESS_DENIED)을 받는다 — 화면은 그 응답을 그대로 안내로 바꾼다.

export type ReviewStatus = "PENDING" | "VERIFIED" | "REJECTED";

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
  status: ReviewStatus | string;
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

export function reviewReport(reportId: string, action: "APPROVE" | "REJECT", reason?: string): Promise<AdminPasserReport> {
  return apiFetch<AdminPasserReport>(`/api/v1/admin/passers/reports/${reportId}`, {
    method: "PATCH",
    body: JSON.stringify({ action, reason }),
  });
}

// 증빙 이미지는 <img src>로 바로 못 연다 — Authorization 헤더가 필요하다.
// blob으로 받아 object URL을 만들어 보여준다. 호출자가 revokeObjectURL로 정리한다.
export async function fetchProofObjectUrl(reportId: string): Promise<string | null> {
  const token = getAccessToken();
  const res = await fetch(`${API_BASE_URL}/api/v1/admin/passers/reports/${reportId}/proof`, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  if (!res.ok) return null;
  return URL.createObjectURL(await res.blob());
}
