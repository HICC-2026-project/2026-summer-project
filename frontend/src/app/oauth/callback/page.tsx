"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { setTokens } from "@/lib/auth";

export default function OAuthCallbackPage() {
  const router = useRouter();

  useEffect(() => {
    const hash = window.location.hash.replace(/^#/, "");
    const params = new URLSearchParams(hash);
    const accessToken = params.get("accessToken");
    const refreshToken = params.get("refreshToken");

    if (accessToken && refreshToken) {
      setTokens(accessToken, refreshToken);
    } else {
      const error = new URLSearchParams(window.location.search).get("error");
      console.error("카카오 로그인 실패:", error ?? "알 수 없는 오류");
    }

    router.replace("/");
  }, [router]);

  return (
    <div
      style={{
        minHeight: "100dvh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        color: "#61616C",
        fontSize: 14,
      }}
    >
      로그인 처리 중...
    </div>
  );
}
