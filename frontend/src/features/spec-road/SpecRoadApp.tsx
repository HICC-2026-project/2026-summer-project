"use client";

import { useEffect, useState } from "react";
import { AnalyzingScreen } from "./screens/AnalyzingScreen";
import { AppScreen } from "./screens/AppScreen";
import { DetailSheet } from "./screens/DetailSheet";
import { LoginScreen } from "./screens/LoginScreen";
import { OnboardingScreen } from "./screens/OnboardingScreen";
import type { OnboardStep, Screen, Spec, Tab, Target } from "./types";

const INITIAL_SPEC: Spec = {
  gpa: "3.8",
  gpaScale: 4.5,
  langType: "TOEIC",
  langScore: "850",
  certs: ["정보처리기사", "SQLD"],
  exps: ["스타트업 인턴 3개월", "교내 개발 동아리"],
};

const INITIAL_TARGET: Target = {
  job: "SW 개발",
  size: "대기업",
  industry: "IT",
};

export function SpecRoadApp() {
  const [screen, setScreen] = useState<Screen>("login");
  const [onboardStep, setOnboardStep] = useState<OnboardStep>(0);
  const [tab, setTab] = useState<Tab>("home");
  const [detailId, setDetailId] = useState<number | null>(null);
  const [spec, setSpec] = useState<Spec>(INITIAL_SPEC);
  const [target, setTarget] = useState<Target>(INITIAL_TARGET);

  useEffect(() => {
    if (screen !== "analyzing") return;
    const timer = setTimeout(() => {
      setScreen("app");
      setTab("home");
    }, 2100);
    return () => clearTimeout(timer);
  }, [screen]);

  function toggleCert(value: string) {
    setSpec((s) => ({
      ...s,
      certs: s.certs.includes(value) ? s.certs.filter((c) => c !== value) : [...s.certs, value],
    }));
  }

  function toggleExp(value: string) {
    setSpec((s) => ({
      ...s,
      exps: s.exps.includes(value) ? s.exps.filter((e) => e !== value) : [...s.exps, value],
    }));
  }

  function handleOnboardNext() {
    if (onboardStep === 0) {
      setOnboardStep(1);
    } else {
      setScreen("analyzing");
    }
  }

  function handleOnboardBack() {
    if (onboardStep === 1) {
      setOnboardStep(0);
    } else {
      setScreen("login");
    }
  }

  return (
    <div
      className="min-h-dvh sm:p-6"
      style={{
        display: "flex",
        justifyContent: "center",
        alignItems: "stretch",
        background: "#E9E9EF",
        fontFamily: '"Pretendard", -apple-system, BlinkMacSystemFont, system-ui, sans-serif',
        color: "#15141B",
      }}
    >
      <div
        className="h-dvh max-h-dvh sm:h-[min(100dvh,900px)] sm:rounded-[28px] sm:shadow-[0_30px_80px_rgba(24,22,44,0.16)]"
        style={{
          position: "relative",
          width: "100%",
          maxWidth: 480,
          background: "#F6F6F9",
          overflow: "hidden",
        }}
      >
        {screen === "login" && (
          <LoginScreen
            onLoginKakao={() => {
              setOnboardStep(0);
              setScreen("onboard");
            }}
            onLoginDemo={() => {
              setTab("home");
              setScreen("app");
            }}
          />
        )}

        {screen === "onboard" && (
          <OnboardingScreen
            step={onboardStep}
            spec={spec}
            target={target}
            onBack={handleOnboardBack}
            onNext={handleOnboardNext}
            onSetGpa={(v) => setSpec((s) => ({ ...s, gpa: v }))}
            onSetGpaScale={(v) =>
              setSpec((s) => ({ ...s, gpaScale: v, gpa: Number(s.gpa) > v ? String(v) : s.gpa }))
            }
            onSetLangType={(v) => setSpec((s) => ({ ...s, langType: v, langScore: "" }))}
            onSetLangScore={(v) => setSpec((s) => ({ ...s, langScore: v }))}
            onToggleCert={toggleCert}
            onToggleExp={toggleExp}
            onSetJob={(v) => setTarget((t) => ({ ...t, job: v }))}
            onSetSize={(v) => setTarget((t) => ({ ...t, size: v }))}
            onSetIndustry={(v) => setTarget((t) => ({ ...t, industry: v }))}
          />
        )}

        {screen === "analyzing" && <AnalyzingScreen />}

        {screen === "app" && (
          <AppScreen
            tab={tab}
            onTabChange={setTab}
            spec={spec}
            target={target}
            onOpenDetail={setDetailId}
            onEditSpec={() => {
              setOnboardStep(0);
              setScreen("onboard");
            }}
            onLogout={() => {
              setDetailId(null);
              setScreen("login");
            }}
          />
        )}

        {screen === "app" && detailId != null && (
          <DetailSheet
            recommendationId={detailId}
            onClose={() => setDetailId(null)}
            onCompare={() => {
              setDetailId(null);
              setTab("compare");
            }}
          />
        )}
      </div>
    </div>
  );
}
