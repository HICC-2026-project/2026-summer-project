import { getAccessToken } from "./auth";

// 프로덕션(Vercel)에서는 same-origin("")으로 호출해 next.config.ts의 /api/* 프록시를 타고,
// 로컬 개발에서는 로컬 백엔드를 직접 호출한다.
const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ??
  (process.env.NODE_ENV === "production" ? "" : "http://localhost:8080");

// 카카오 로그인 시작은 페이지 이동(navigation)이라 Mixed Content 차단 대상이 아니고,
// OAuth 인가 요청 쿠키가 백엔드 도메인에 설정되어야 하므로 프록시 없이 백엔드로 직접 간다.
export const BACKEND_ORIGIN =
  process.env.NEXT_PUBLIC_BACKEND_ORIGIN ??
  (process.env.NODE_ENV === "production" ? "http://13.124.80.94:8080" : "http://localhost:8080");

export const KAKAO_LOGIN_URL = `${BACKEND_ORIGIN}/oauth2/authorization/kakao`;

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const accessToken = typeof window !== "undefined" ? getAccessToken() : null;

  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...options?.headers,
    },
  });

  if (!res.ok) {
    throw new ApiError(res.status, `${res.status} ${res.statusText}`);
  }

  return res.json() as Promise<T>;
}
