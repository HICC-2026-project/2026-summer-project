"use client";

import { PRIMARY } from "../data";

interface LoginScreenProps {
  onLoginKakao: () => void;
  onLoginDemo: () => void;
}

export function LoginScreen({ onLoginKakao, onLoginDemo }: LoginScreenProps) {
  return (
    <div
      style={{
        position: "absolute",
        inset: 0,
        display: "flex",
        flexDirection: "column",
        padding: "0 28px",
        background: "linear-gradient(180deg, #FBFBFE 0%, #F1EFFC 100%)",
        animation: "cfFade .5s ease both",
      }}
    >
      <div style={{ flex: 1, display: "flex", flexDirection: "column", justifyContent: "center" }}>
        <div
          style={{
            width: 60,
            height: 60,
            borderRadius: 18,
            background: `linear-gradient(145deg, ${PRIMARY}, color-mix(in srgb, ${PRIMARY} 60%, #9d7bff))`,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            boxShadow: `0 12px 28px color-mix(in srgb, ${PRIMARY} 40%, transparent)`,
            marginBottom: 26,
          }}
        >
          <span style={{ color: "#fff", fontSize: 30, fontWeight: 800, lineHeight: 1 }}>↗</span>
        </div>
        <div style={{ fontSize: 15, fontWeight: 700, color: PRIMARY, letterSpacing: "-0.01em", marginBottom: 10 }}>
          Spec Road
        </div>
        <h1
          style={{
            fontSize: 30,
            fontWeight: 800,
            letterSpacing: "-0.03em",
            lineHeight: 1.28,
            margin: "0 0 14px",
            color: "#15141B",
          }}
        >
          합격자 데이터로
          <br />
          완성하는 나의
          <br />
          스펙 로드맵
        </h1>
        <p style={{ fontSize: 15, lineHeight: 1.6, color: "#61616C", margin: 0, maxWidth: 300 }}>
          내 스펙을 입력하면 익명 합격자 데이터와 비교해, 지금 해야 할 활동을 알려드려요.
        </p>
      </div>
      <div style={{ paddingBottom: 40, display: "flex", flexDirection: "column", gap: 10 }}>
        <button
          type="button"
          onClick={onLoginKakao}
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: 8,
            width: "100%",
            height: 54,
            border: "none",
            borderRadius: 16,
            background: "#FEE500",
            color: "#191600",
            fontSize: 16,
            fontWeight: 700,
            cursor: "pointer",
          }}
        >
          <span style={{ fontSize: 18 }}>💬</span> 카카오로 3초 만에 시작하기
        </button>
        <button
          type="button"
          onClick={onLoginDemo}
          style={{
            width: "100%",
            height: 50,
            border: "1px solid #E1E0EA",
            borderRadius: 16,
            background: "transparent",
            color: "#61616C",
            fontSize: 15,
            fontWeight: 600,
            cursor: "pointer",
          }}
        >
          로그인 없이 둘러보기
        </button>
        <p style={{ textAlign: "center", fontSize: 12, color: "#9797A1", margin: "6px 0 0", lineHeight: 1.5 }}>
          Google 로그인은 곧 지원될 예정이에요
        </p>
      </div>
    </div>
  );
}
