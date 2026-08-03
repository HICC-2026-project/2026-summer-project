"use client";

import { useEffect, useState } from "react";
import { PRIMARY } from "../data";

const ANALYZE_STEPS = [
  { label: "스펙 프로필 정리 완료", done: true },
  { label: "유사 합격자 데이터 검색 중", done: true },
  { label: "AI 맞춤 추천 생성", done: false },
];

// AI 생성이 길어질 때 멈춘 것처럼 보이지 않도록 안내를 덧붙이는 시점.
const LONG_WAIT_MS = 8000;

export function AnalyzingScreen() {
  const [longWait, setLongWait] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setLongWait(true), LONG_WAIT_MS);
    return () => clearTimeout(timer);
  }, []);

  return (
    <div
      style={{
        position: "absolute",
        inset: 0,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "0 40px",
        background: "linear-gradient(180deg, #FBFBFE, #EAF1FE)",
        animation: "cfFade .3s ease both",
      }}
    >
      <div style={{ position: "relative", width: 84, height: 84, marginBottom: 28 }}>
        <div style={{ position: "absolute", inset: 0, borderRadius: "50%", border: "4px solid #DCE7FE" }} />
        <div
          style={{
            position: "absolute",
            inset: 0,
            borderRadius: "50%",
            border: "4px solid transparent",
            borderTopColor: PRIMARY,
            animation: "cfSpin .9s linear infinite",
          }}
        />
        <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 30 }}>
          ✦
        </div>
      </div>
      <h2 style={{ fontSize: 20, fontWeight: 800, color: "#15141B", margin: "0 0 8px", letterSpacing: "-0.02em", textAlign: "center" }}>
        합격자 데이터와
        <br />
        비교 분석 중이에요
      </h2>
      <p style={{ fontSize: 14, color: "#61616C", margin: "0 0 26px", textAlign: "center" }}>잠시만 기다려 주세요</p>
      <div style={{ display: "flex", flexDirection: "column", gap: 10, width: "100%", maxWidth: 280 }}>
        {ANALYZE_STEPS.map((a) => (
          <div
            key={a.label}
            style={{
              display: "flex",
              alignItems: "center",
              gap: 10,
              fontSize: 13.5,
              fontWeight: 600,
              color: a.done ? "#15141B" : "#B0B0BA",
            }}
          >
            <span
              style={{
                width: 20,
                height: 20,
                borderRadius: "50%",
                background: a.done ? "#12A150" : "#D9D8E4",
                color: "#fff",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: 11,
                flexShrink: 0,
              }}
            >
              {a.done ? "✓" : ""}
            </span>
            {a.label}
          </div>
        ))}
      </div>

      {longWait && (
        <p
          style={{
            fontSize: 12.5,
            color: "#9797A1",
            margin: "22px 0 0",
            textAlign: "center",
            lineHeight: 1.55,
            animation: "cfFade .4s ease both",
          }}
        >
          AI가 활동을 하나씩 검토하고 있어요.
          <br />
          최대 30초 정도 걸릴 수 있어요.
        </p>
      )}
    </div>
  );
}
