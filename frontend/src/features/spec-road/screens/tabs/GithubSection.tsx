"use client";

import { useEffect, useRef, useState } from "react";
import { ApiError } from "@/lib/api";
import { deleteGithubProfile, getGithubProfile, postGithubAnalysis } from "../../api";
import { BADGE, PRIMARY } from "../../data";
import type { GithubProfile } from "../../types";

// PENDING 폴링 간격·상한 (E3 1단계 계약: 3초 간격, 최대 2분).
const POLL_INTERVAL_MS = 3000;
const POLL_TIMEOUT_MS = 120000;

const CONSENT_TEXT =
  "소유한 공개 레포와 공개 커밋이 검색되는 기여 레포를 분석합니다 · 코드 원문은 저장하지 않고 직무 비율 등 파생값만 저장합니다 · 연결 해제 시 분석 결과와 자동 추가된 경험이 삭제됩니다";

const RATE_LIMIT_MESSAGE = "GitHub 호출 한도에 걸렸어요. 잠시 후 재시도해 주세요.";

// DONE인데 집계된 직무 비율·레포가 하나도 없을 때(소유 공개 레포 0개 등) 보여줄 안내.
// 막대·레포 목록이 통째로 비어 분석일과 버튼 2개만 남으면 "결과가 어디 있지"로 보인다.
const EMPTY_RESULT_MESSAGE =
  "분석할 공개 활동을 찾지 못했어요. 본인 소유 공개 레포가 없거나 공개 커밋이 검색되지 않는 계정이에요. 레포를 공개로 전환하거나 커밋 후 재분석해 보세요.";

function barRowStyle() {
  return { display: "flex", alignItems: "center", gap: 10, padding: "7px 0" } as const;
}

interface GithubSectionViewProps {
  profile: GithubProfile | null;
  urlInput: string;
  onUrlInputChange: (value: string) => void;
  submitting: boolean;
  submitError: string | null;
  pollTimedOut: boolean;
  onSubmit: () => void;
  onReanalyze: () => void;
  onDisconnect: () => void;
}

// 순수 표시 컴포넌트 — 상태(profile)를 props로만 받아 미연결/PENDING/DONE/FAILED·RATE_LIMITED
// 네 분기를 그린다. 데이터 요청·폴링은 컨테이너(GithubSection)가 맡는다 — CompareTab처럼
// 상태 분기 렌더링만 따로 테스트할 수 있게 하기 위해서다.
export function GithubSectionView({
  profile,
  urlInput,
  onUrlInputChange,
  submitting,
  submitError,
  pollTimedOut,
  onSubmit,
  onReanalyze,
  onDisconnect,
}: GithubSectionViewProps) {
  if (!profile || !profile.connected) {
    return (
      <div>
        <div style={{ display: "flex", gap: 8, marginBottom: 10 }}>
          <input
            value={urlInput}
            onChange={(e) => onUrlInputChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                onSubmit();
              }
            }}
            type="text"
            placeholder="username 또는 github.com/username"
            style={{
              flex: 1,
              height: 44,
              padding: "0 14px",
              borderRadius: 12,
              border: "1px solid #E1E0EA",
              fontSize: 14,
              outline: "none",
            }}
          />
          <button
            type="button"
            onClick={onSubmit}
            disabled={submitting || !urlInput.trim()}
            style={{
              height: 44,
              padding: "0 16px",
              borderRadius: 12,
              border: "none",
              background: PRIMARY,
              color: "#fff",
              fontSize: 13.5,
              fontWeight: 700,
              cursor: submitting || !urlInput.trim() ? "not-allowed" : "pointer",
              opacity: submitting || !urlInput.trim() ? 0.6 : 1,
              flexShrink: 0,
              whiteSpace: "nowrap",
            }}
          >
            분석 시작
          </button>
        </div>
        {submitError && (
          <div style={{ fontSize: 12.5, color: BADGE.bad.color, marginBottom: 8, lineHeight: 1.5 }}>{submitError}</div>
        )}
        <p style={{ fontSize: 11.5, color: "#9797A1", lineHeight: 1.6, margin: 0 }}>{CONSENT_TEXT}</p>
      </div>
    );
  }

  if (profile.status === "PENDING") {
    return (
      <div>
        <div style={{ fontSize: 13.5, fontWeight: 700, color: "#15141B" }}>{profile.username} 분석 중이에요</div>
        <p style={{ fontSize: 12, color: "#9797A1", marginTop: 6, lineHeight: 1.55 }}>
          {pollTimedOut
            ? "시간이 좀 걸리고 있어요. 잠시 후 새로고침해 주세요."
            : "완료되면 자동으로 반영돼요. 이 화면을 열어둔 채 잠시 기다려 주세요."}
        </p>
      </div>
    );
  }

  if (profile.status === "FAILED" || profile.status === "RATE_LIMITED") {
    const message =
      profile.failureReason ?? (profile.status === "RATE_LIMITED" ? RATE_LIMIT_MESSAGE : "분석에 실패했어요.");
    return (
      <div>
        <div style={{ fontSize: 13.5, color: BADGE.bad.color, marginBottom: 10, lineHeight: 1.5 }}>{message}</div>
        {submitError && (
          <div style={{ fontSize: 12.5, color: BADGE.bad.color, marginBottom: 8, lineHeight: 1.5 }}>{submitError}</div>
        )}
        <button
          type="button"
          onClick={onReanalyze}
          disabled={submitting}
          style={{
            height: 42,
            padding: "0 18px",
            borderRadius: 12,
            border: "none",
            background: PRIMARY,
            color: "#fff",
            fontSize: 13.5,
            fontWeight: 700,
            cursor: submitting ? "not-allowed" : "pointer",
            opacity: submitting ? 0.6 : 1,
          }}
        >
          다시 시도
        </button>
      </div>
    );
  }

  // DONE
  const ratios = profile.jobRatios ?? [];
  const repos = profile.repos ?? [];
  const hasResults = ratios.length > 0 || repos.length > 0;
  return (
    <div>
      {!hasResults && (
        <div style={{ fontSize: 13, color: "#61616C", lineHeight: 1.6, marginBottom: 12 }}>{EMPTY_RESULT_MESSAGE}</div>
      )}

      {ratios.length > 0 && (
        <div style={{ marginBottom: 14 }}>
          {ratios.map((r) => {
            const percent = Math.round(r.ratio * 100);
            return (
              <div key={r.jobType} style={barRowStyle()}>
                <span style={{ width: 64, fontSize: 12.5, fontWeight: 700, color: "#15141B", flexShrink: 0 }}>{r.label}</span>
                <div style={{ flex: 1, height: 8, borderRadius: 999, background: "#F1F0F6", overflow: "hidden" }}>
                  <div style={{ height: "100%", borderRadius: 999, background: PRIMARY, width: `${percent}%` }} />
                </div>
                <span style={{ width: 38, textAlign: "right", fontSize: 12, fontWeight: 700, color: "#61616C", flexShrink: 0 }}>
                  {percent}%
                </span>
              </div>
            );
          })}
        </div>
      )}

      {profile.targetJobMatchRatio != null && (
        <div style={{ fontSize: 13, fontWeight: 700, color: PRIMARY, marginBottom: 12 }}>
          목표 직무 관련 기여 {Math.round(profile.targetJobMatchRatio * 100)}%
        </div>
      )}

      {repos.length > 0 && (
        <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 12 }}>
          {repos.map((repo) => (
            <div
              key={repo.name}
              style={{
                padding: "9px 12px",
                borderRadius: 12,
                background: "#F6F5FA",
                border: "1px solid #EAE9F1",
                fontSize: 12.5,
                color: "#4A4954",
                lineHeight: 1.5,
              }}
            >
              <span style={{ fontWeight: 700, color: "#15141B" }}>{repo.name}</span> · {repo.primaryJobLabel} · 커밋{" "}
              {repo.commits}회 · {repo.mainLanguage}
            </div>
          ))}
        </div>
      )}

      {profile.analyzedAt && (
        <div style={{ fontSize: 11.5, color: "#B0B0BA", marginBottom: 12 }}>{profile.analyzedAt.slice(0, 10)} 분석</div>
      )}

      {submitError && (
        <div style={{ fontSize: 12.5, color: BADGE.bad.color, marginBottom: 10, lineHeight: 1.5 }}>{submitError}</div>
      )}

      <div style={{ display: "flex", gap: 8 }}>
        <button
          type="button"
          onClick={onReanalyze}
          disabled={submitting}
          style={{
            flex: 1,
            height: 42,
            borderRadius: 12,
            border: "1px solid #E1E0EA",
            background: "#fff",
            color: "#15141B",
            fontSize: 13.5,
            fontWeight: 700,
            cursor: submitting ? "not-allowed" : "pointer",
            opacity: submitting ? 0.6 : 1,
          }}
        >
          재분석
        </button>
        <button
          type="button"
          onClick={onDisconnect}
          style={{
            flex: 1,
            height: 42,
            borderRadius: 12,
            border: "1px solid #F0D8D8",
            background: "#fff",
            color: BADGE.bad.color,
            fontSize: 13.5,
            fontWeight: 700,
            cursor: "pointer",
          }}
        >
          연결 해제
        </button>
      </div>
    </div>
  );
}

interface GithubSectionProps {
  /** 분석 완료·연결 해제 뒤 경험이 포함된 스펙을 다시 불러오도록 부모에 알린다. */
  onSpecRefresh: () => void;
}

// 데이터 요청·폴링을 담당하는 컨테이너. 렌더링은 GithubSectionView에 위임한다.
export function GithubSection({ onSpecRefresh }: GithubSectionProps) {
  const [profile, setProfile] = useState<GithubProfile | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [urlInput, setUrlInput] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [pollTimedOut, setPollTimedOut] = useState(false);
  // 이전 status를 기억해 PENDING → DONE으로 막 넘어간 순간에만 스펙을 새로고침한다.
  const prevStatus = useRef<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getGithubProfile()
      .then((p) => {
        if (cancelled) return;
        setProfile(p);
        prevStatus.current = p.connected ? p.status : null;
      })
      .catch(() => {})
      .finally(() => {
        if (!cancelled) setLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // PENDING 상태일 때만 3초 간격으로 폴링하고, 2분이 지나면 중단한다.
  // 언마운트되거나 상태가 PENDING을 벗어나면 인터벌을 정리한다.
  // (pollTimedOut 리셋은 여기서 하지 않는다 — 새 분석 시작 시 startAnalysis가 직접 끈다.
  // PENDING이 아닐 때는 어차피 화면에 쓰이지 않는 값이라, effect 본문에서 동기 setState를
  // 피하기 위해 그대로 둔다.)
  useEffect(() => {
    if (!profile?.connected || profile.status !== "PENDING") {
      return;
    }

    let cancelled = false;
    const startedAt = Date.now();

    const interval = setInterval(() => {
      if (Date.now() - startedAt >= POLL_TIMEOUT_MS) {
        if (!cancelled) setPollTimedOut(true);
        clearInterval(interval);
        return;
      }
      getGithubProfile()
        .then((p) => {
          if (cancelled) return;
          setProfile(p);
          if (p.connected && p.status !== "PENDING" && prevStatus.current === "PENDING") {
            onSpecRefresh();
          }
          prevStatus.current = p.connected ? p.status : null;
        })
        .catch(() => {});
    }, POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      clearInterval(interval);
    };
    // profile 객체 전체가 아니라 connected 여부·status만 의존성으로 둔다 —
    // jobRatios 등 다른 필드가 바뀔 때마다 폴링 인터벌을 새로 만들 필요가 없다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [profile?.connected, profile?.connected ? profile.status : null, onSpecRefresh]);

  if (!loaded) return null;

  async function startAnalysis(url: string) {
    setSubmitting(true);
    setSubmitError(null);
    try {
      const res = await postGithubAnalysis(url);
      setProfile({
        connected: true,
        username: res.username,
        status: "PENDING",
        failureReason: null,
        analyzedAt: null,
        commitTotal: null,
        activeMonths: null,
        jobRatios: null,
        targetJobMatchRatio: null,
        repos: null,
      });
      prevStatus.current = "PENDING";
      setPollTimedOut(false);
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : "분석을 시작하지 못했어요. 잠시 후 다시 시도해주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  function handleSubmit() {
    const trimmed = urlInput.trim();
    if (!trimmed) return;
    void startAnalysis(trimmed);
  }

  function handleReanalyze() {
    if (!profile?.connected) return;
    void startAnalysis(profile.username);
  }

  async function handleDisconnect() {
    if (!window.confirm("연결을 해제할까요? 분석 결과와 자동으로 추가된 경험이 함께 삭제돼요.")) return;
    try {
      await deleteGithubProfile();
      setProfile({ connected: false });
      prevStatus.current = null;
      setUrlInput("");
      setSubmitError(null);
      onSpecRefresh();
    } catch (error) {
      window.alert(error instanceof ApiError ? error.message : "연결 해제에 실패했어요. 잠시 후 다시 시도해주세요.");
    }
  }

  return (
    <div style={{ background: "#fff", border: "1px solid #EDEDF2", borderRadius: 20, padding: "16px 18px", marginBottom: 16 }}>
      <div style={{ fontSize: 14, color: "#61616C", fontWeight: 500, marginBottom: 12 }}>GitHub 공개 레포 분석</div>
      <GithubSectionView
        profile={profile}
        urlInput={urlInput}
        onUrlInputChange={setUrlInput}
        submitting={submitting}
        submitError={submitError}
        pollTimedOut={pollTimedOut}
        onSubmit={handleSubmit}
        onReanalyze={handleReanalyze}
        onDisconnect={() => void handleDisconnect()}
      />
    </div>
  );
}
