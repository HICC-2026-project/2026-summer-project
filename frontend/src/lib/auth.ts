const ACCESS_TOKEN_KEY = "specroad_access_token";
const REFRESH_TOKEN_KEY = "specroad_refresh_token";

export function setTokens(accessToken: string, refreshToken: string) {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

// 토큰 재발급까지 실패해 다시 로그인해야 하는 상황을 화면에 알린다.
// (apiFetch는 화면 구조를 모르므로 앱이 핸들러를 등록해 로그인 화면으로 되돌린다)
type SessionExpiredHandler = () => void;

let sessionExpiredHandler: SessionExpiredHandler | null = null;

export function onSessionExpired(handler: SessionExpiredHandler): () => void {
  sessionExpiredHandler = handler;
  return () => {
    if (sessionExpiredHandler === handler) sessionExpiredHandler = null;
  };
}

export function notifySessionExpired() {
  clearTokens();
  sessionExpiredHandler?.();
}
