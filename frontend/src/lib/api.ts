import { getAccessToken, getRefreshToken, notifySessionExpired, setTokens } from "./auth";

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

function request(path: string, options: RequestInit | undefined, accessToken: string | null) {
  const headers = new Headers(options?.headers);
  const isFormData = typeof FormData !== "undefined" && options?.body instanceof FormData;

  // multipart/form-data는 브라우저가 boundary를 포함한 Content-Type을 직접 만들어야 한다.
  if (!isFormData && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  return fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  });
}

// 액세스 토큰은 1시간 뒤 만료된다. 만료된 채로 앱을 열면 모든 API가 401이 되므로
// 리프레시 토큰으로 재발급해 원래 요청을 한 번 더 시도한다.
// 백엔드가 리프레시 토큰도 함께 교체(로테이션)하므로 두 토큰을 모두 저장한다.
async function reissueTokens(): Promise<string> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) throw new ApiError(401, "리프레시 토큰이 없습니다.");

  const res = await request("/api/v1/auth/refresh", {
    method: "POST",
    body: JSON.stringify({ refreshToken }),
  }, null);

  if (!res.ok) throw new ApiError(res.status, "토큰 재발급에 실패했습니다.");

  const tokens = (await res.json()) as { accessToken: string; refreshToken: string };
  setTokens(tokens.accessToken, tokens.refreshToken);
  return tokens.accessToken;
}

// 여러 요청이 동시에 401을 받아도 재발급은 한 번만 하고 결과를 함께 기다린다.
// (동시에 재발급하면 로테이션 때문에 뒤늦은 요청이 이미 폐기된 토큰을 쓰게 된다)
let reissueInFlight: Promise<string> | null = null;

function reissueOnce(): Promise<string> {
  if (!reissueInFlight) {
    reissueInFlight = reissueTokens().finally(() => {
      reissueInFlight = null;
    });
  }
  return reissueInFlight;
}

async function parseBody<T>(res: Response): Promise<T> {
  // 로그아웃 등 204 No Content 응답은 본문이 없어 json() 파싱이 실패한다.
  if (res.status === 204 || res.headers.get("content-length") === "0") {
    return undefined as T;
  }
  return res.json() as Promise<T>;
}

export async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const accessToken = typeof window !== "undefined" ? getAccessToken() : null;

  let res = await request(path, options, accessToken);

  if (res.status === 401 && typeof window !== "undefined" && getRefreshToken()) {
    try {
      const newAccessToken = await reissueOnce();
      res = await request(path, options, newAccessToken);
    } catch {
      // 리프레시 토큰까지 만료·폐기된 상태 → 재로그인이 필요하다.
      notifySessionExpired();
      throw new ApiError(401, "세션이 만료되었습니다. 다시 로그인해주세요.");
    }
  }

  if (!res.ok) {
    throw new ApiError(res.status, await extractErrorMessage(res));
  }

  return parseBody<T>(res);
}

// 백엔드 GlobalExceptionHandler는 { code, message, timestamp } 형태로 응답한다.
// 검증 실패 메시지("학점은 필수입니다." 등)를 그대로 화면에 보여주기 위해 꺼낸다.
async function extractErrorMessage(res: Response): Promise<string> {
  try {
    const body = (await res.json()) as { message?: string };
    if (body?.message) return body.message;
  } catch {
    // 본문이 비었거나 JSON이 아니면 상태 코드로 대체한다.
  }
  return `${res.status} ${res.statusText}`;
}
