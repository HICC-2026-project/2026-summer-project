"use client";

import { useEffect, useState } from "react";
import { ApiError, KAKAO_LOGIN_URL } from "@/lib/api";
import { clearTokens, getAccessToken } from "@/lib/auth";
import { getMe, getRecommendations, getRoadmap, putSpec, putTarget } from "./api";
import { RECOMMENDATIONS, ROADMAP } from "./data";
import {
  fromLanguageScoresPayload,
  fromRecommendationsResponse,
  fromRoadmapResponse,
  normalizeJobCode,
  toRecommendationMeta,
} from "./helpers";
import { AnalyzingScreen } from "./screens/AnalyzingScreen";
import { AppScreen } from "./screens/AppScreen";
import { DetailSheet } from "./screens/DetailSheet";
import { IntroScreen } from "./screens/IntroScreen";
import { LoginScreen } from "./screens/LoginScreen";
import { OnboardingScreen } from "./screens/OnboardingScreen";
import type {
  OnboardStep,
  Recommendation,
  RecommendationMeta,
  RoadmapMilestone,
  Screen,
  Spec,
  Tab,
  Target,
} from "./types";

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
  job: "BACKEND",
  size: "대기업",
  industry: "IT·플랫폼",
};

// 로그인 유저의 첫 온보딩은 직무도 직접 고르게 한다.
const EMPTY_TARGET: Target = {
  job: "",
  size: "대기업",
  industry: "IT·플랫폼",
};

export function SpecRoadApp() {
  const [screen, setScreen] = useState<Screen>("login");
  const [onboardStep, setOnboardStep] = useState<OnboardStep>(0);
  const [tab, setTab] = useState<Tab>("home");
  const [detailId, setDetailId] = useState<string | number | null>(null);
  const [spec, setSpec] = useState<Spec>(INITIAL_SPEC);
  const [target, setTarget] = useState<Target>(INITIAL_TARGET);
  const [nickname, setNickname] = useState<string | null>(null);

  // 추천 목록 상태. 로그인 유저는 실 API(GET /recommendations)로 채우고,
  // 둘러보기(비로그인)는 목업으로 채운다. meta는 응답 최상단 요약(matchScore 등).
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [recMeta, setRecMeta] = useState<RecommendationMeta | null>(null);
  const [recLoading, setRecLoading] = useState(false);
  const [recError, setRecError] = useState(false);

  // 로드맵 상태. 추천과 동일하게 로그인 유저는 실 API(GET /roadmaps), 둘러보기는 목업.
  const [roadmap, setRoadmap] = useState<RoadmapMilestone[]>([]);
  const [roadmapLoading, setRoadmapLoading] = useState(false);
  const [roadmapError, setRoadmapError] = useState(false);

  useEffect(() => {
    if (screen !== "analyzing") return;
    const timer = setTimeout(() => {
      setScreen("app");
      setTab("home");
    }, 2100);
    return () => clearTimeout(timer);
  }, [screen]);

  // 앱 화면 진입 시 추천을 불러온다. 로그인 상태면 실 API, 아니면(둘러보기) 목업.
  useEffect(() => {
    if (screen !== "app") return;

    if (!getAccessToken()) {
      setRecommendations(RECOMMENDATIONS);
      setRecMeta(null);
      setRecError(false);
      setRoadmap(ROADMAP);
      setRoadmapError(false);
      return;
    }

    let cancelled = false;

    setRecLoading(true);
    setRecError(false);
    getRecommendations()
      .then((res) => {
        if (cancelled) return;
        setRecommendations(fromRecommendationsResponse(res));
        setRecMeta(toRecommendationMeta(res));
      })
      .catch(() => {
        if (cancelled) return;
        setRecommendations([]);
        setRecMeta(null);
        setRecError(true);
      })
      .finally(() => {
        if (!cancelled) setRecLoading(false);
      });

    setRoadmapLoading(true);
    setRoadmapError(false);
    getRoadmap()
      .then((res) => {
        if (cancelled) return;
        setRoadmap(fromRoadmapResponse(res));
      })
      .catch(() => {
        if (cancelled) return;
        setRoadmap([]);
        setRoadmapError(true);
      })
      .finally(() => {
        if (!cancelled) setRoadmapLoading(false);
      });

    return () => {
      cancelled = true;
    };
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
        // 직무 코드 도입 전 저장된 한글 직무명은 미선택으로 떨어져 온보딩에서 다시 고르게 된다.
        const jobCode = normalizeJobCode(me.target?.jobType);
        if (me.target) {
          setTarget({
            job: jobCode,
            size: me.target.companySize,
            industry: me.target.industry,
          });
        }

        // 스펙과 유효한 직무가 모두 있어야 추천·로드맵이 정상 동작한다.
        if (me.spec && jobCode !== "") {
          setScreen("app");
          setTab("home");
        } else {
          if (!me.spec) setSpec(EMPTY_SPEC);
          if (!me.target) setTarget(EMPTY_TARGET);
          setOnboardStep(0);
          setScreen("onboard");
        }
      })
      .catch(() => {
        clearTokens();
      });
  }, []);

  // 백엔드 OAuth 엔드포인트로 이동 → 카카오 로그인 → /oauth/callback으로 토큰과 함께 복귀
  function startKakaoLogin() {
    window.location.href = KAKAO_LOGIN_URL;
  }

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
      } catch (error) {
        // 백엔드 검증 메시지("학점은 필수입니다." 등)를 그대로 보여준다.
        const message =
          error instanceof ApiError ? error.message : "네트워크 확인 후 다시 시도해주세요.";
        alert(`스펙 저장에 실패했어요.\n${message}`);
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
            onLoginKakao={startKakaoLogin}
            // 7/14 회의 결정: 게스트 모드 대신 서비스 소개 페이지를 보여준다.
            onLoginDemo={() => setScreen("intro")}
          />
        )}

        {screen === "intro" && (
          <IntroScreen
            onBack={() => setScreen("login")}
            onLoginKakao={startKakaoLogin}
            onPreviewDemo={() => {
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
            recommendations={recommendations}
            recMeta={recMeta}
            recLoading={recLoading}
            recError={recError}
            roadmap={roadmap}
            roadmapLoading={roadmapLoading}
            roadmapError={roadmapError}
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
            recommendations={recommendations}
            recMeta={recMeta}
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
