"use client";

import { useEffect, useState, type CSSProperties } from "react";
import Link from "next/link";
import { getMyPasserReports } from "../../api";
import { ExperienceCard } from "../../components/ExperienceCard";
import { BADGE, DEMO_USER_NAME, PRIMARY } from "../../data";
import { hasMeaningfulLangScore, jobLabel } from "../../helpers";
import type { MyPasserReport, ReviewStatus, Spec, Target } from "../../types";
import { GithubSection } from "./GithubSection";

// 검수 상태 → 배지. 관리자 화면 탭 라벨과 같은 말을 쓴다.
const REVIEW_BADGE: Record<ReviewStatus, { color: string; bg: string; text: string }> = {
  PENDING: { ...BADGE.warn, text: "검수 대기" },
  VERIFIED: { ...BADGE.ok, text: "반영 완료" },
  REJECTED: { ...BADGE.bad, text: "반려됨" },
};

interface ProfileTabProps {
  spec: Spec;
  target: Target;
  nickname: string | null;
  /** 관리자(검수자) 계정이면 검수 화면 링크를 보여준다. */
  isAdmin?: boolean;
  /** 비로그인 예시 화면 여부. 저장할 계정이 없어 수정 대신 로그인을 유도한다. */
  isDemo: boolean;
  onEditSpec: () => void;
  onOpenPasserReport: () => void;
  onLogout: () => void;
  /** 회원 탈퇴. 예시 화면에서는 없다. */
  onWithdraw?: () => void;
  /** 닉네임 수정. 예시 화면에서는 없다. */
  onEditNickname?: () => void;
  /** GitHub 분석 완료·연결 해제 뒤 경험이 포함된 스펙을 다시 불러온다. 예시 화면에서는 없다. */
  onSpecRefresh?: () => void;
}

function rowStyle(hasBorder: boolean): CSSProperties {
  return {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    padding: "15px 0",
    borderBottom: hasBorder ? "1px solid #F1F0F6" : "none",
  };
}

export function ProfileTab({
  spec,
  target,
  nickname,
  isAdmin = false,
  isDemo,
  onEditSpec,
  onOpenPasserReport,
  onLogout,
  onWithdraw,
  onEditNickname,
  onSpecRefresh,
}: ProfileTabProps) {
  const displayName = nickname ?? DEMO_USER_NAME;
  const targetSummary = `${target.size} ${jobLabel(target.job)}`;
  const certLabel = spec.certs.length ? spec.certs.join(", ") : "없음";
  // 0점 입력은 미입력으로 취급한다 — "TOEIC 0"이 프로필에 보이면 없는 성적이 있는 것처럼 보인다.
  const langEntries = Object.entries(spec.langScores).filter(([type, score]) => hasMeaningfulLangScore(type, score));
  const langLabel = langEntries.length ? langEntries.map(([type, score]) => `${type} ${score}`).join(", ") : "없음";

  // 내 제보 검수 상태. 제보 직후엔 "검수 대기"만 보이므로, 사용자가 "반영됐나?"를 여기서 확인한다.
  // 실패는 조용히 비운다 — 프로필 화면의 본 기능이 아니라서 오류 카드까지 띄우지 않는다.
  const [myReports, setMyReports] = useState<MyPasserReport[]>([]);
  useEffect(() => {
    if (isDemo) return;
    let cancelled = false;
    getMyPasserReports()
      .then((list) => {
        if (!cancelled) setMyReports(list);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [isDemo]);

  return (
    <div style={{ padding: "22px 20px 108px", animation: "cfUp .35s ease both" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 15, marginBottom: 24 }}>
        <div
          style={{
            width: 60,
            height: 60,
            borderRadius: 20,
            background: `linear-gradient(145deg, ${PRIMARY}, color-mix(in srgb, ${PRIMARY} 60%, #7FA6FF))`,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "#fff",
            fontSize: 24,
            fontWeight: 800,
            flexShrink: 0,
          }}
        >
          {displayName.slice(0, 1)}
        </div>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <div style={{ fontSize: 20, fontWeight: 800, color: "#15141B", letterSpacing: "-0.02em" }}>{displayName}</div>
            {!isDemo && onEditNickname && (
              <button
                type="button"
                onClick={onEditNickname}
                aria-label="닉네임 수정"
                style={{ border: "1px solid #E1E0EA", background: "#fff", borderRadius: 8, padding: "3px 8px", fontSize: 11.5, color: "#61616C", cursor: "pointer" }}
              >
                수정
              </button>
            )}
          </div>
          <div style={{ fontSize: 13.5, color: "#61616C", marginTop: 2 }}>{targetSummary} 준비 중</div>
        </div>
      </div>

      {isDemo && (
        <div
          style={{
            display: "flex",
            gap: 9,
            padding: "12px 14px",
            marginBottom: 16,
            background: `color-mix(in srgb, ${PRIMARY} 7%, #fff)`,
            border: `1px solid color-mix(in srgb, ${PRIMARY} 18%, #fff)`,
            borderRadius: 14,
          }}
        >
          <span style={{ fontSize: 15, lineHeight: 1.4, flexShrink: 0 }}>👀</span>
          <span style={{ fontSize: 13, color: "#4A4954", lineHeight: 1.5 }}>
            예시 데이터를 보고 있어요. 로그인하면 내 스펙으로 추천을 받을 수 있어요.
          </span>
        </div>
      )}

      <div style={{ background: "#fff", border: "1px solid #EDEDF2", borderRadius: 20, padding: "6px 18px", marginBottom: 16 }}>
        <div style={rowStyle(true)}>
          <span style={{ fontSize: 14, color: "#61616C", fontWeight: 500 }}>학점</span>
          <span style={{ fontSize: 15, fontWeight: 700, color: "#15141B" }}>{spec.gpa} / {spec.gpaScale}</span>
        </div>
        <div style={{ ...rowStyle(true), alignItems: "flex-start", gap: 20 }}>
          <span style={{ fontSize: 14, color: "#61616C", fontWeight: 500, flexShrink: 0 }}>어학</span>
          <span style={{ fontSize: 14, fontWeight: 600, color: "#15141B", textAlign: "right", lineHeight: 1.5 }}>{langLabel}</span>
        </div>
        <div style={{ ...rowStyle(false), alignItems: "flex-start", gap: 20 }}>
          <span style={{ fontSize: 14, color: "#61616C", fontWeight: 500, flexShrink: 0 }}>자격증</span>
          <span style={{ fontSize: 14, fontWeight: 600, color: "#15141B", textAlign: "right", lineHeight: 1.5 }}>{certLabel}</span>
        </div>
      </div>

      <div style={{ background: "#fff", border: "1px solid #EDEDF2", borderRadius: 20, padding: "16px 18px", marginBottom: 16 }}>
        <div style={{ fontSize: 14, color: "#61616C", fontWeight: 500, marginBottom: spec.experiences.length ? 12 : 0 }}>
          경험
        </div>
        {spec.experiences.length ? (
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            {spec.experiences.map((exp, idx) => (
              <ExperienceCard key={idx} experience={exp} />
            ))}
          </div>
        ) : (
          <div style={{ fontSize: 14, fontWeight: 600, color: "#9797A1" }}>경험 미입력</div>
        )}
      </div>

      {/* GitHub 공개 레포 분석(E3 1단계). 예시 화면은 저장할 계정이 없어 섹션 자체를 숨긴다. */}
      {!isDemo && onSpecRefresh && <GithubSection onSpecRefresh={onSpecRefresh} />}

      {/* 예시 화면은 저장할 계정이 없어 수정 대신 로그인을 안내한다. */}
      <button
        type="button"
        onClick={onEditSpec}
        style={{
          width: "100%",
          height: 52,
          border: isDemo ? "none" : "1px solid #E1E0EA",
          borderRadius: 16,
          background: isDemo ? "#FEE500" : "#fff",
          color: isDemo ? "#191600" : "#15141B",
          fontSize: 15,
          fontWeight: 700,
          cursor: "pointer",
          marginBottom: 10,
        }}
      >
        {isDemo ? "카카오로 시작하고 내 스펙 입력하기" : "스펙 · 목표 수정하기"}
      </button>

      {!isDemo && (
        <>
          <button
            type="button"
            onClick={onOpenPasserReport}
            style={{
              width: "100%",
              height: 52,
              border: `1px solid color-mix(in srgb, ${PRIMARY} 24%, #E1E0EA)`,
              borderRadius: 16,
              background: `color-mix(in srgb, ${PRIMARY} 6%, #fff)`,
              color: PRIMARY,
              fontSize: 15,
              fontWeight: 700,
              cursor: "pointer",
            }}
          >
            합격자 스펙 제보하기
          </button>
          <p style={{ margin: "9px 8px 0", fontSize: 11.5, color: "#9797A1", lineHeight: 1.5, textAlign: "center" }}>
            제보 내용은 익명으로 저장되며, 검수 완료 후 비교 데이터에 반영됩니다.
          </p>

          {isAdmin && (
            <Link
              href="/admin"
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                marginTop: 12,
                height: 46,
                border: "1px dashed #C9C7D6",
                borderRadius: 14,
                color: "#4A4954",
                fontSize: 13.5,
                fontWeight: 700,
                textDecoration: "none",
              }}
            >
              🛠 제보 검수 화면 (관리자)
            </Link>
          )}

          {myReports.length > 0 && (
            <div style={{ marginTop: 16, background: "#fff", border: "1px solid #EDEDF2", borderRadius: 18, padding: "6px 18px" }}>
              <div style={{ fontSize: 12, color: "#9797A1", fontWeight: 600, padding: "12px 0 4px" }}>내 제보 {myReports.length}건</div>
              {myReports.map((r, i) => {
                const badge = REVIEW_BADGE[r.status] ?? REVIEW_BADGE.PENDING;
                return (
                  <div key={r.reportId} style={rowStyle(i < myReports.length - 1)}>
                    <div>
                      <div style={{ fontSize: 14, fontWeight: 700, color: "#15141B" }}>
                        {r.jobTypeLabel} · {r.year}년 합격
                      </div>
                      <div style={{ fontSize: 11.5, color: "#9797A1", marginTop: 2 }}>
                        {r.createdAt.slice(0, 10)} 제보
                      </div>
                    </div>
                    <span
                      style={{
                        fontSize: 12,
                        fontWeight: 700,
                        color: badge.color,
                        background: badge.bg,
                        padding: "4px 10px",
                        borderRadius: 999,
                        whiteSpace: "nowrap",
                      }}
                    >
                      {badge.text}
                    </span>
                  </div>
                );
              })}
            </div>
          )}
        </>
      )}

      <p style={{ textAlign: "center", fontSize: 12, color: "#B0B0BA", margin: "22px 0 0" }}>
        <a
          href="#"
          onClick={(e) => {
            e.preventDefault();
            onLogout();
          }}
          style={{ color: "#B0B0BA" }}
        >
          {isDemo ? "처음 화면으로" : "로그아웃"}
        </a>
      </p>
      {!isDemo && onWithdraw && (
        <p style={{ textAlign: "center", fontSize: 11.5, color: "#C9C7D6", margin: "10px 0 0" }}>
          <a
            href="#"
            onClick={(e) => {
              e.preventDefault();
              onWithdraw();
            }}
            style={{ color: "#C9C7D6" }}
          >
            회원 탈퇴
          </a>
        </p>
      )}
    </div>
  );
}
