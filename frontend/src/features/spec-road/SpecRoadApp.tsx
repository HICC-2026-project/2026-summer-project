"use client";

import { useEffect, useState } from "react";
import { KAKAO_LOGIN_URL } from "@/lib/api";
import { clearTokens, getAccessToken } from "@/lib/auth";
import { getMe, putSpec, putTarget } from "./api";
import { fromLanguageScoresPayload } from "./helpers";
import { AnalyzingScreen } from "./screens/AnalyzingScreen";
import { AppScreen } from "./screens/AppScreen";
import { DetailSheet } from "./screens/DetailSheet";
import { LoginScreen } from "./screens/LoginScreen";
import { OnboardingScreen } from "./screens/OnboardingScreen";
import type { OnboardStep, Screen, Spec, Tab, Target } from "./types";

// "로그인 없이 둘러보기"용 예시 데이터. 실제 로그인 유저의 첫 온보딩은 EMPTY_SPEC에서 시작한다.
const INITIAL_SPEC: Spec = {
  gpa: "3.8",
  gpaScale: 4.5,
  grade: 3,
  langScores: { TOEIC: "850" },
  certs: ["정보처리기사", "SQLD"],
};

const EMPTY_SPEC: Spec = {
  gpa: "",
  gpaScale: 4.5,
  grade: null,
  langScores: {},
  certs: [],
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
  const [nickname, setNickname] = useState<string | null>(null);

  useEffect(() => {
    if (screen !== "analyzing") return;
    const timer = setTimeout(() => {
      setScreen("app");
      setTab("home");
    }, 2100);
    return () => clearTimeout(timer);
  }, [screen]);

  // 카카오 로그인 콜백(app/oauth/callback)이 토큰을 저장해두면 로그인 화면을 건너뛰고
  // 실제 로그인한 유저 정보를 가져온다. 스펙이 이미 있으면 홈으로, 없으면(첫 로그인) 온보딩으로.
  useEffect(() => {
    if (!getAccessToken()) return;

    getMe()
      .then((me) => {
        setNickname(me.nickname);

        if (me.spec) {
          setSpec({
            gpa: me.spec.gpa != null ? String(me.spec.gpa) : "",
            gpaScale: me.spec.gpaMax ?? 4.5,
            grade: me.spec.grade ?? null,
            langScores: fromLanguageScoresPayload(me.spec.languageScores ?? []),
            certs: me.spec.certifications ?? [],
          });
        }
        if (me.target) {
          setTarget({
            job: me.target.jobType,
            size: me.target.companySize,
            industry: me.target.industry,
          });
        }

        if (me.spec) {
          setScreen("app");
          setTab("home");
        } else {
          setSpec(EMPTY_SPEC);
          setOnboardStep(0);
          setScreen("onboard");
        }
      })
      .catch(() => {
        clearTokens();
      });
  }, []);

  function addCert(value: string) {
    setSpec((s) => (s.certs.includes(value) ? s : { ...s, certs: [...s.certs, value] }));
  }

  function removeCert(value: string) {
    setSpec((s) => ({ ...s, certs: s.certs.filter((c) => c !== value) }));
  }

  // 온보딩 완료 시 스펙·목표 직무를 저장한다. 둘러보기(비로그인) 모드는 API 없이 화면만 진행.
  async function handleOnboardNext() {
    if (onboardStep === 0) {
      setOnboardStep(1);
      return;
    }

    if (getAccessToken()) {
      try {
        await Promise.all([putSpec(spec), putTarget(target)]);
      } catch {
        alert("스펙 저장에 실패했어요. 네트워크 확인 후 다시 시도해주세요.");
        return;
      }
    }
    setScreen("analyzing");
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
              // 백엔드 OAuth 엔드포인트로 이동 → 카카오 로그인 → /oauth/callback으로 토큰과 함께 복귀
              window.location.href = KAKAO_LOGIN_URL;
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
            onSetGrade={(v) => setSpec((s) => ({ ...s, grade: v }))}
            onSetLangScore={(type, v) =>
              setSpec((s) => ({ ...s, langScores: { ...s.langScores, [type]: v } }))
            }
            onAddCert={addCert}
            onRemoveCert={removeCert}
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
            nickname={nickname}
            onOpenDetail={setDetailId}
            onEditSpec={() => {
              setOnboardStep(0);
              setScreen("onboard");
            }}
            onLogout={() => {
              clearTokens();
              setNickname(null);
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
