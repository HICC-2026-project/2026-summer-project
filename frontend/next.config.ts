import type { NextConfig } from "next";

// Vercel(HTTPS) 페이지에서 EC2(HTTP) API를 직접 호출하면 브라우저가 Mixed Content로 차단한다.
// /api/* 요청을 Vercel 서버가 대신 백엔드로 전달(프록시)해 이 제한을 우회한다.
// 카카오 로그인 시작(/oauth2/...)은 페이지 이동이라 차단 대상이 아니며,
// OAuth 쿠키가 백엔드 도메인에 설정되어야 하므로 프록시를 태우지 않는다.
const BACKEND_ORIGIN =
  process.env.BACKEND_ORIGIN ?? "http://13.124.80.94:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${BACKEND_ORIGIN}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
