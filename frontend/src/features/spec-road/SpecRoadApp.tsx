"use client";

import { useEffect, useRef, useState } from "react";
import { ApiError, KAKAO_LOGIN_URL } from "@/lib/api";
import { clearTokens, getAccessToken, onSessionExpired } from "@/lib/auth";
import { getMe, getRecommendations, getRoadmap, postLogout, postPasserReport, putSpec, putTarget } from "./api";
import { RECOMMENDATIONS, ROADMAP } from "./data";
import {
  fromLanguageScoresPayload,
  fromRecommendationsResponse,
  fromRoadmapResponse,
  normalizeJobCode,
  toRecommendationMeta,
} from "./helpers";
import { clearLastResult, loadLastResult, saveLastResult } from "./recommendationCache";
import { AnalyzingScreen } from "./screens/AnalyzingScreen";
import { AppScreen } from "./screens/AppScreen";
import { DetailSheet } from "./screens/DetailSheet";
import { IntroScreen } from "./screens/IntroScreen";
import { LoginScreen } from "./screens/LoginScreen";
import { OnboardingScreen } from "./screens/OnboardingScreen";
import { PasserReportScreen } from "./screens/PasserReportScreen";
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

// 분석 화면 표시 시간의 하한·상한 (AI 응답을 기다리되 양 극단을 막는다).
const ANALYZING_MIN_MS = 1800;
const ANALYZING_MAX_MS = 40000;

export function SpecRoadApp() {
  const [screen, setScreen] = useState<Screen>("login");
  const [onboardStep, setOnboardStep] = useState<OnboardStep>(0);
  // 온보딩에 어디서 들어왔는지 기억한다. 첫 로그인(스펙 없음)은 "login"에서, 프로필의
  // "스펙·목표 수정하기"는 "profile"에서 진입하는데, 뒤로가기가 이걸 구분하지 않으면
  // 로그인 상태로 프로필에서 들어온 사용자까지 카카오 로그인 화면으로 튕겨 나간다.
  const [onboardFrom, setOnboardFrom] = useState<"login" | "profile">("login");
  const [tab, setTab] = useState<Tab>("home");
  const [detailId, setDetailId] = useState<string | number | null>(null);
  const [spec, setSpec] = useState<Spec>(INITIAL_SPEC);
  const [target, setTarget] = useState<Target>(INITIAL_TARGET);
  const [nickname, setNickname] = useState<string | null>(null);
  // 저장된 직전 결과가 다른 계정 것과 섞이지 않도록 사용자 식별자를 함께 보관한다.
  const [userId, setUserId] = useState<string | null>(null);
  // 비로그인 예시 화면 여부. 저장할 계정이 없으므로 프로필에서 수정 대신 로그인을 유도한다.
  const [isDemo, setIsDemo] = useState(false);

  // 분석 화면 진입 시각. 로딩 상태가 바뀌어도 기준 시각이 초기화되지 않도록 ref로 둔다.
  const analyzingStartedAt = useRef<number | null>(null);
  // 분석 화면과 앱 화면에서 같은 데이터를 중복 요청하지 않기 위한 1회 실행 표시.
  const dataRequested = useRef(false);

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
  // 하루 갱신 한도(3회)에 막혀 이전 로드맵을 보고 있는지. 안내가 없으면 사용자는
  // 스펙을 바꿨는데 로드맵이 그대로인 것을 버그로 인지한다.
  const [roadmapLimitReached, setRoadmapLimitReached] = useState(false);

  // 로그인 화면으로 되돌리며 유저별 상태를 모두 비운다(다음 로그인 유저에게 남지 않도록).
  function resetToLogin() {
    clearLastResult();
    setUserId(null);
    setNickname(null);
    setDetailId(null);
    setRecommendations([]);
    setRecMeta(null);
    setRoadmap([]);
    // 별도 상태라 recMeta처럼 자동으로 비워지지 않는다 — 안 비우면 이전 유저의
    // "한도 도달" 배너가 다음 로그인 유저의 첫 화면에 잠깐 남는다.
    setRoadmapLimitReached(false);
    setIsDemo(false);
    setScreen("login");
  }

  // 분석 화면은 AI 응답이 도착할 때까지 유지한다.
  // 응답이 빨리 오면 화면이 깜빡이므로 최소 시간을 두고,
  // 백엔드 타임아웃(30초)을 넘겨도 멈춘 것처럼 보이지 않도록 최대 시간을 둔다.
  useEffect(() => {
    if (screen !== "analyzing") {
      analyzingStartedAt.current = null;
      return;
    }
    if (analyzingStartedAt.current == null) analyzingStartedAt.current = Date.now();

    const elapsed = Date.now() - analyzingStartedAt.current;
    const stillLoading = recLoading || roadmapLoading;
    const wait = stillLoading
      ? Math.max(0, ANALYZING_MAX_MS - elapsed)
      : Math.max(0, ANALYZING_MIN_MS - elapsed);

    const timer = setTimeout(() => {
      setScreen("app");
      setTab("home");
    }, wait);
    return () => clearTimeout(timer);
  }, [screen, recLoading, roadmapLoading]);

  // 리프레시 토큰까지 만료돼 재발급이 실패하면 로그인 화면으로 돌려보낸다.
  useEffect(() => onSessionExpired(resetToLogin), []);

  // 추천·로드맵을 불러온다. 분석 화면에서 요청을 시작해, 결과가 준비된 뒤 홈으로 넘어가게 한다.
  // (앱 화면에 바로 진입하는 경우 — 기존 유저 재방문·둘러보기 — 도 같은 요청을 쓴다)
  useEffect(() => {
    if (screen !== "analyzing" && screen !== "app") {
      if (screen === "login") dataRequested.current = false;
      return;
    }
    if (dataRequested.current) return;
    dataRequested.current = true;

    // 둘러보기(비로그인)는 API를 부르지 않는다. 목업은 상태에 넣지 않고 렌더 시점에 그대로 쓴다.
    if (!getAccessToken()) return;

    let cancelled = false;

    // 직전 결과가 있으면 먼저 보여준다. 스펙을 바꾸지 않았다면 서버도 같은 값을 돌려주므로,
    // 매번 "AI가 준비하고 있어요"를 다시 보는 대신 화면이 곧바로 채워진다.
    const cached = userId ? loadLastResult(userId) : null;
    const hasCache = cached != null;

    // 화면 진입을 계기로 데이터를 받아오는 구간이라 로딩 표시를 여기서 켤 수밖에 없다.
    // (set-state-in-effect 규칙은 파생 가능한 상태를 겨냥한 것이고, 여기서는 외부 요청의 진행 상태다)
    /* eslint-disable react-hooks/set-state-in-effect */
    if (cached) {
      setRecommendations(cached.recommendations);
      setRecMeta(cached.recMeta);
      setRoadmap(cached.roadmap);
    }

    // 캐시를 이미 띄웠으면 로딩 문구 대신 기존 화면을 유지한 채 뒤에서 갱신한다.
    setRecLoading(!hasCache);
    setRecError(false);
    setRoadmapLoading(!hasCache);
    setRoadmapError(false);
    // 매 사이클 시작 시 초기화 — 안 하면 이번 요청이 실패했을 때 이전 사이클의
    // "한도 도달" 배너가 에러 문구와 동시에 떠 서로 모순된 설명이 된다.
    setRoadmapLimitReached(false);

    const latest: { recommendations?: Recommendation[]; recMeta?: RecommendationMeta | null; roadmap?: RoadmapMilestone[] } = {};

    getRecommendations()
      .then((res) => {
        if (cancelled) return;
        const list = fromRecommendationsResponse(res);
        const meta = toRecommendationMeta(res);
        latest.recommendations = list;
        latest.recMeta = meta;
        setRecommendations(list);
        setRecMeta(meta);
      })
      .catch(() => {
        if (cancelled) return;
        // 갱신에 실패하면 캐시가 있어도 그대로 두지 않는다.
        // 옛 점수를 계속 보여주면 사용자는 최신 결과로 오해하게 된다.
        setRecommendations([]);
        setRecMeta(null);
        setRecError(true);
      })
      .finally(() => {
        if (!cancelled) setRecLoading(false);
      });

    getRoadmap()
      .then((res) => {
        if (cancelled) return;
        const steps = fromRoadmapResponse(res);
        latest.roadmap = steps;
        setRoadmap(steps);
        setRoadmapLimitReached(res.dailyLimitReached ?? false);
      })
      .catch(() => {
        if (cancelled) return;
        setRoadmap([]);
        setRoadmapError(true);
      })
      .finally(() => {
        if (cancelled) return;
        setRoadmapLoading(false);
        // 두 요청이 모두 끝난 시점에 최신 결과를 저장한다(다음 로그인 때 즉시 표시용).
        if (userId && latest.recommendations) {
          saveLastResult({
            userId,
            recommendations: latest.recommendations,
            recMeta: latest.recMeta ?? null,
            roadmap: latest.roadmap ?? [],
          });
        }
      });
    /* eslint-enable react-hooks/set-state-in-effect */

    return () => {
      cancelled = true;
    };
  }, [screen, userId]);

  // 카카오 로그인 콜백(app/oauth/callback)이 토큰을 저장해두면 로그인 화면을 건너뛰고
  // 실제 로그인한 유저 정보를 가져온다. 스펙이 이미 있으면 홈으로, 없으면(첫 로그인) 온보딩으로.
  useEffect(() => {
    if (!getAccessToken()) return;

    getMe()
      .then((me) => {
        setNickname(me.nickname);
        setUserId(me.id);

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
          setOnboardFrom("login");
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
    // 스펙이 바뀌었으니 분석 화면에서 추천을 새로 요청한다.
    // 저장해 둔 직전 결과는 지운다. 남겨두면 예전 점수를 새 결과인 것처럼 보여주게 된다.
    clearLastResult();
    dataRequested.current = false;
    setScreen("analyzing");
  }

  function handleOnboardBack() {
    if (onboardStep === 1) {
      setOnboardStep(0);
    } else if (onboardFrom === "profile") {
      // 프로필의 "스펙·목표 수정하기"로 들어온 경우 — 로그인 상태이므로 프로필로 되돌린다.
      // (예전엔 무조건 로그인 화면으로 보내, 로그인한 사용자가 수정을 취소만 해도
      // 카카오 로그인 페이지로 튕겨 나갔다)
      setScreen("app");
      setTab("profile");
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
              setIsDemo(true);
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

        {screen === "passer-report" && (
          <PasserReportScreen
            initialSpec={spec}
            initialJob={target.job}
            onBack={() => {
              setScreen("app");
              setTab("profile");
            }}
            onSubmit={postPasserReport}
          />
        )}

        {screen === "app" && (
          <AppScreen
            tab={tab}
            onTabChange={setTab}
            spec={spec}
            target={target}
            nickname={nickname}
            recommendations={isDemo ? RECOMMENDATIONS : recommendations}
            recMeta={isDemo ? null : recMeta}
            recLoading={isDemo ? false : recLoading}
            recError={isDemo ? false : recError}
            roadmap={isDemo ? ROADMAP : roadmap}
            roadmapLoading={isDemo ? false : roadmapLoading}
            roadmapError={isDemo ? false : roadmapError}
            roadmapLimitReached={isDemo ? false : roadmapLimitReached}
            isDemo={isDemo}
            onOpenDetail={setDetailId}
            onOpenPasserReport={() => setScreen("passer-report")}
            onEditSpec={() => {
              // 예시 화면에는 저장할 계정이 없어, 수정 대신 로그인으로 보낸다.
              if (isDemo) {
                startKakaoLogin();
                return;
              }
              setOnboardStep(0);
              setOnboardFrom("profile");
              setScreen("onboard");
            }}
            onLogout={() => {
              // 서버의 리프레시 토큰 폐기는 실패해도 로그아웃 자체는 진행한다.
              void postLogout().catch(() => {});
              clearTokens();
              resetToLogin();
            }}
          />
        )}

        {screen === "app" && detailId != null && (
          <DetailSheet
            recommendationId={detailId}
            recommendations={isDemo ? RECOMMENDATIONS : recommendations}
            recMeta={isDemo ? null : recMeta}
            onClose={() => setDetailId(null)}
          />
        )}
      </div>
    </div>
  );
}
