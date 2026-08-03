"use client";

import type { CSSProperties } from "react";
import { PRIMARY } from "../data";

interface IntroScreenProps {
  onBack: () => void;
  onLoginKakao: () => void;
  onPreviewDemo: () => void;
}

const sectionTitleStyle: CSSProperties = {
  fontSize: 18,
  fontWeight: 800,
  letterSpacing: "-0.02em",
  color: "#15141B",
  margin: "0 0 12px",
};

const cardStyle: CSSProperties = {
  background: "#fff",
  border: "1px solid #EDEDF2",
  borderRadius: 18,
  padding: 16,
};

// 기능 명세서 F-01~F-05를 사용자 언어로 옮긴 소개용 목록.
const FEATURES: { icon: string; title: string; body: string }[] = [
  {
    icon: "📝",
    title: "내 스펙 기록",
    body: "학점·어학·자격증을 한 번만 입력하면 이후 모든 추천의 기준이 돼요.",
  },
  {
    icon: "✦",
    title: "AI 맞춤 활동 추천",
    body: "목표 직무에 맞는 인턴십·대외활동·공모전을 이유와 함께 골라드려요.",
  },
  {
    icon: "📊",
    title: "합격자 스펙 비교",
    body: "익명 합격자 데이터와 항목별로 비교해 무엇이 부족한지 수치로 보여줘요.",
  },
  {
    icon: "🗺️",
    title: "커리어 로드맵",
    body: "학기와 방학을 나눠, 지금 해야 할 일부터 순서대로 정리해드려요.",
  },
];

const STEPS: { step: string; title: string; body: string }[] = [
  { step: "1", title: "카카오로 로그인", body: "닉네임만 받아요. 이메일·연락처는 수집하지 않아요." },
  { step: "2", title: "스펙과 목표 입력", body: "학점·학년과 희망 직무만 있으면 바로 시작할 수 있어요." },
  { step: "3", title: "추천과 로드맵 확인", body: "지금 지원할 활동과 시기별 계획을 함께 받아요." },
];

export function IntroScreen({ onBack, onLoginKakao, onPreviewDemo }: IntroScreenProps) {
  return (
    <div
      style={{
        position: "absolute",
        inset: 0,
        display: "flex",
        flexDirection: "column",
        background: "#F6F6F9",
        animation: "cfFade .4s ease both",
      }}
    >
      <div style={{ padding: "18px 20px 8px", display: "flex", alignItems: "center", gap: 12, flexShrink: 0 }}>
        <button
          type="button"
          onClick={onBack}
          aria-label="뒤로"
          style={{
            width: 36,
            height: 36,
            borderRadius: 10,
            border: "none",
            background: "#fff",
            boxShadow: "0 1px 2px rgba(24,22,44,0.06)",
            color: "#61616C",
            fontSize: 18,
            cursor: "pointer",
            flexShrink: 0,
          }}
        >
          ‹
        </button>
        <span style={{ fontSize: 15, fontWeight: 700, color: "#15141B" }}>서비스 소개</span>
      </div>

      <div className="cf-scroll" style={{ flex: 1, overflowY: "auto", padding: "8px 20px 20px" }}>
        <div style={{ fontSize: 14, fontWeight: 700, color: PRIMARY, marginBottom: 8 }}>Spec Road</div>
        <h1 style={{ fontSize: 25, fontWeight: 800, letterSpacing: "-0.03em", lineHeight: 1.3, margin: "0 0 12px", color: "#15141B" }}>
          막막한 취업 준비를
          <br />
          순서가 있는 계획으로
        </h1>
        <p style={{ fontSize: 14.5, lineHeight: 1.6, color: "#61616C", margin: "0 0 26px" }}>
          &ldquo;지금 뭘 해야 하지?&rdquo; 취업 준비의 가장 큰 어려움은 정보가 없어서가 아니라, 흩어진 정보 중 나에게 맞는
          것이 무엇인지 모른다는 데 있어요. Spec Road는 내 스펙을 합격자 데이터와 비교해, 지금 해야 할 활동을 알려줍니다.
        </p>

        <h2 style={sectionTitleStyle}>이런 걸 할 수 있어요</h2>
        <div style={{ display: "flex", flexDirection: "column", gap: 10, marginBottom: 26 }}>
          {FEATURES.map((feature) => (
            <div key={feature.title} style={{ ...cardStyle, display: "flex", gap: 12 }}>
              <span style={{ fontSize: 19, lineHeight: 1.3, flexShrink: 0 }}>{feature.icon}</span>
              <span style={{ minWidth: 0 }}>
                <span style={{ display: "block", fontSize: 15, fontWeight: 700, color: "#15141B", marginBottom: 4 }}>
                  {feature.title}
                </span>
                <span style={{ display: "block", fontSize: 13.5, color: "#61616C", lineHeight: 1.55 }}>{feature.body}</span>
              </span>
            </div>
          ))}
        </div>

        <h2 style={sectionTitleStyle}>이렇게 시작해요</h2>
        <div style={{ display: "flex", flexDirection: "column", gap: 10, marginBottom: 26 }}>
          {STEPS.map((item) => (
            <div key={item.step} style={{ ...cardStyle, display: "flex", gap: 12, alignItems: "flex-start" }}>
              <span
                style={{
                  width: 26,
                  height: 26,
                  borderRadius: 9,
                  background: `color-mix(in srgb, ${PRIMARY} 12%, #fff)`,
                  color: PRIMARY,
                  fontSize: 13.5,
                  fontWeight: 800,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  flexShrink: 0,
                }}
              >
                {item.step}
              </span>
              <span style={{ minWidth: 0 }}>
                <span style={{ display: "block", fontSize: 14.5, fontWeight: 700, color: "#15141B", marginBottom: 3 }}>
                  {item.title}
                </span>
                <span style={{ display: "block", fontSize: 13.5, color: "#61616C", lineHeight: 1.55 }}>{item.body}</span>
              </span>
            </div>
          ))}
        </div>

        <h2 style={sectionTitleStyle}>데이터를 이렇게 다뤄요</h2>
        <div style={{ ...cardStyle, marginBottom: 8 }}>
          <p style={{ fontSize: 13.5, color: "#61616C", lineHeight: 1.6, margin: 0 }}>
            합격자 데이터는 <b style={{ color: "#15141B" }}>이름·학교 없이 익명</b>으로만 저장하고, 로그인 시에도 카카오
            닉네임만 받아요. 아직 데이터가 충분하지 않은 활동은 그 사실을 화면에 그대로 알려드리고, 대신 AI 일반 추천을
            제공합니다.
          </p>
        </div>
      </div>

      <div
        style={{
          padding: "12px 20px calc(20px + env(safe-area-inset-bottom))",
          background: "linear-gradient(180deg, rgba(246,246,249,0), #F6F6F9 32%)",
          display: "flex",
          flexDirection: "column",
          gap: 9,
          flexShrink: 0,
        }}
      >
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
          onClick={onPreviewDemo}
          style={{
            width: "100%",
            height: 48,
            border: "1px solid #E1E0EA",
            borderRadius: 16,
            background: "transparent",
            color: "#61616C",
            fontSize: 14.5,
            fontWeight: 600,
            cursor: "pointer",
          }}
        >
          예시 화면 먼저 보기
        </button>
      </div>
    </div>
  );
}
