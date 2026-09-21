"use client";

import { useState } from "react";
import { deleteRecommendationFeedback, postRecommendationFeedback } from "../../api";
import { BADGE, DEMO_SPEC_POSITION, DEMO_USER_NAME, INK, INK_FAINT, INK_MUTED, LINE, PRIMARY, SURFACE_MUTED } from "../../data";
import { StateMessage } from "../../components/StateMessage";
import { dday, ddayColor, hasMeaningfulLangScore, jobLabel, percentileLabel } from "../../helpers";
import type { ReactionType, Recommendation, RecommendationMeta, Spec, Target } from "../../types";

interface HomeTabProps {
  spec: Spec;
  target: Target;
  nickname: string | null;
  isDemo: boolean;
  recommendations: Recommendation[];
  recMeta: RecommendationMeta | null;
  recLoading: boolean;
  recError: boolean;
  onOpenDetail: (id: string | number) => void;
}

// 히어로 카드 안의 칩 — 축·갭·표본 수가 같은 모양을 쓴다.
const HERO_CHIP = { fontSize: 12.5, fontWeight: 700, background: "rgba(255,255,255,0.16)", padding: "6px 11px", borderRadius: 999, whiteSpace: "nowrap" } as const;

export function HomeTab({ spec, target, nickname, isDemo, recommendations, recMeta, recLoading, recError, onOpenDetail }: HomeTabProps) {
  const targetSummary = `${target.size} ${jobLabel(target.job)}`;
  const displayName = nickname ?? DEMO_USER_NAME;

  // E10-2(F-09) — 서버가 준 myReaction 위에, 이번 세션에서 클릭한 결과만 낙관적으로 덮어쓴다.
  // recommendations가 새로 오면(재조회) 초기화된다 — 옛 오버라이드가 새 응답과 어긋나지 않도록.
  const [reactionOverrides, setReactionOverrides] = useState<Record<string, ReactionType | null>>({});
  // 첫 피드백 등록·반응 변경 직후 "다음 추천 갱신 때 반영돼요" 안내를 보여줄 활동 id 집합.
  const [feedbackNotice, setFeedbackNotice] = useState<Record<string, boolean>>({});

  async function handleReaction(activityId: string, reaction: ReactionType) {
    const current = reactionOverrides[activityId] ?? recommendations.find((r) => String(r.id) === activityId)?.myReaction ?? null;
    const next = current === reaction ? null : reaction;
    try {
      if (next === null) {
        await deleteRecommendationFeedback(activityId);
      } else {
        await postRecommendationFeedback(activityId, reaction);
      }
      setReactionOverrides((prev) => ({ ...prev, [activityId]: next }));
      setFeedbackNotice((prev) => ({ ...prev, [activityId]: true }));
    } catch {
      // 부가 기능이라 별도 에러 카드는 띄우지 않는다(닉네임 변경 등과 같은 정책) — 버튼 상태가
      // 그대로면 사용자가 다시 눌러볼 수 있다.
    }
  }

  // 입력한 어학·자격증을 요약한다. 둘 다 없으면 안내 문구를 보여준다.
  // 어학은 0점 입력을 미입력으로 취급한다(hasMeaningfulLangScore 주석 참고).
  const langCount = Object.entries(spec.langScores).filter(([type, v]) => hasMeaningfulLangScore(type, v)).length;
  // 자격증은 "내가 입력한 개수"를 그대로 보여준다. 비교 탭의 자격증 축은 "합격자 프로필과
  // 매칭된 개수"라는 다른 개념이고, 매칭·미매칭 목록을 화면에서 직접 구분해 보여주므로
  // (예전 matchScore 체계처럼) 같은 라벨의 두 숫자가 어긋나 보이는 문제가 없다.
  const certLabel = spec.certs.length === 0 ? null : `자격증 ${spec.certs.length}개`;
  const specSummary =
    langCount + spec.certs.length === 0
      ? "미입력"
      : [langCount > 0 ? `어학 ${langCount}개` : null, certLabel].filter(Boolean).join(" · ");

  // 위치·갭 요약은 실 API 응답(recMeta.specPosition)에서만 나온다.
  // 예시 화면에서만 목업(DEMO_SPEC_POSITION)을 쓰고, 로그인 사용자는 응답이 없으면(로딩·실패)
  // 수치를 감춘다 — 값이 없을 때 목업으로 대체하면 실제 결과인 것처럼 보이기 때문이다.
  // basis가 NONE이면(표본 미달) 축 percentile도 없으므로 문구(basisMessage)만 보여준다.
  const position = isDemo ? DEMO_SPEC_POSITION : (recMeta?.specPosition ?? null);
  const hasPosition = position != null && position.basis !== "NONE";
  const positionSubtitle = position?.basisMessage ?? "";
  const isFallbackRec = recMeta != null && !recMeta.isAiRecommendation;

  return (
    <div style={{ padding: "22px 20px 108px", animation: "cfUp .35s ease both" }}>
      <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", marginBottom: 22 }}>
        <div>
          <p style={{ fontSize: 14, color: INK_MUTED, margin: "0 0 4px", fontWeight: 500 }}>{displayName}님, 안녕하세요 👋</p>
          <h1 style={{ fontSize: 23, fontWeight: 800, letterSpacing: "-0.02em", margin: 0, color: INK, lineHeight: 1.3 }}>
            {targetSummary}
            <br />
            준비를 위한 추천이에요
          </h1>
        </div>
      </div>

      <div
        style={{
          background: `linear-gradient(140deg, ${PRIMARY}, color-mix(in srgb, ${PRIMARY} 62%, #7FA6FF))`,
          borderRadius: 22,
          padding: "20px 22px",
          color: "#fff",
          marginBottom: 14,
          boxShadow: `0 14px 30px color-mix(in srgb, ${PRIMARY} 34%, transparent)`,
          position: "relative",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            position: "absolute",
            right: -30,
            top: -30,
            width: 130,
            height: 130,
            borderRadius: "50%",
            background: "rgba(255,255,255,0.10)",
          }}
        />
        <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12.5, fontWeight: 600, opacity: 0.9, marginBottom: 12 }}>
          <span>✦</span> 합격자 분포 속 내 위치
        </div>
        {/* 예전의 "종합 준비도 N점"은 임의 가중치 합산이라 의미를 설명할 수 없어 없앴다.
            대신 축별 percentile 위치를 그대로 보여준다 — "학점 상위 28%"는 자체 설명이 된다. */}
        {hasPosition && !recLoading ? (
          <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
            {position!.axes.map((a) => (
              <span key={a.axis} style={HERO_CHIP}>
                {a.label} {percentileLabel(a.percentile)}
              </span>
            ))}
            {position!.gaps.length > 0 && (
              <span style={HERO_CHIP}>
                보완 추천 {position!.gaps.length}개
              </span>
            )}
            {/* 표본 수 — 몇 명과 비교한 결과인지 카드 안에서 바로 읽히게 한다. */}
            <span style={{ ...HERO_CHIP, fontWeight: 600, background: "rgba(255,255,255,0.10)", opacity: 0.9 }}>
              {position!.basis === "JOB" && position!.targetJobLabel ? `${position!.targetJobLabel} 합격자` : "전체 합격자"} {position!.sampleSize}명 기준
            </span>
          </div>
        ) : (
          <div style={{ fontSize: 34, fontWeight: 800, lineHeight: 1, letterSpacing: "-0.03em" }}>–</div>
        )}
        {/* 비교 문구는 폴백 상황("BACKEND 합격자 데이터가 부족해, 직무 구분 없이…")에서
            꽤 길어진다 — 줄바꿈을 허용하고, 한국어가 어절 중간에서 끊기지 않게 keep-all을 쓴다. */}
        <div style={{ marginTop: 10, fontSize: 12, opacity: 0.85, lineHeight: 1.45, wordBreak: "keep-all", overflowWrap: "anywhere" }}>
          {recLoading ? "분석 중이에요" : positionSubtitle}
        </div>
      </div>

      {/* 강점·보완 판정은 합격자 평균과의 비교(F-04)가 있어야 가능하다.
          아직 그 API가 없어, 로그인 사용자에게는 판정 대신 본인이 입력한 값을 그대로 보여준다. */}
      <div style={{ display: "flex", gap: 10, marginBottom: 26 }}>
        {isDemo ? (
          <>
            <div style={{ flex: 1, background: "#fff", border: `1px solid ${LINE}`, borderRadius: 16, padding: 14 }}>
              <div style={{ fontSize: 12, color: INK_FAINT, fontWeight: 600, marginBottom: 6 }}>강점</div>
              <div style={{ fontSize: 15, fontWeight: 700, color: "#12A150" }}>학점 · 자격증</div>
            </div>
            <div style={{ flex: 1, background: "#fff", border: `1px solid ${LINE}`, borderRadius: 16, padding: 14 }}>
              <div style={{ fontSize: 12, color: INK_FAINT, fontWeight: 600, marginBottom: 6 }}>보완 필요</div>
              <div style={{ fontSize: 15, fontWeight: 700, color: "#E5484D" }}>어학 · 실무경험</div>
            </div>
          </>
        ) : (
          <>
            <div style={{ flex: 1, background: "#fff", border: `1px solid ${LINE}`, borderRadius: 16, padding: 14 }}>
              <div style={{ fontSize: 12, color: INK_FAINT, fontWeight: 600, marginBottom: 6 }}>내 학점</div>
              <div style={{ fontSize: 15, fontWeight: 700, color: INK }}>
                {spec.gpa ? `${spec.gpa} / ${spec.gpaScale}` : "미입력"}
              </div>
            </div>
            <div style={{ flex: 1, background: "#fff", border: `1px solid ${LINE}`, borderRadius: 16, padding: 14 }}>
              <div style={{ fontSize: 12, color: INK_FAINT, fontWeight: 600, marginBottom: 6 }}>어학 · 자격증</div>
              <div style={{ fontSize: 15, fontWeight: 700, color: INK }}>{specSummary}</div>
            </div>
          </>
        )}
      </div>

      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 14 }}>
        <h2 style={{ fontSize: 18, fontWeight: 800, letterSpacing: "-0.02em", margin: 0, color: INK }}>
          지금 지원하면 좋은 활동
        </h2>
        <span
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 4,
            fontSize: 11.5,
            fontWeight: 700,
            color: isFallbackRec ? INK_FAINT : PRIMARY,
            background: isFallbackRec ? SURFACE_MUTED : `color-mix(in srgb, ${PRIMARY} 11%, #fff)`,
            padding: "5px 9px",
            borderRadius: 999,
          }}
        >
          {isFallbackRec ? "일반 추천" : "✦ AI 추천"}
        </span>
      </div>

      {/* 추천 갱신 정책 안내: 스펙을 바꿔야 새로 생성되고, 하루 3회까지만 가능하다. 로그인 유저에게만 표시. */}
      {recMeta != null && (
        <p style={{ fontSize: 12, color: INK_FAINT, margin: "0 0 12px", lineHeight: 1.5 }}>
          스펙을 수정하면 추천을 새로 만들어요. 하루 3번까지 호출할 수 있어요.
        </p>
      )}

      {/* 하루 한도에 막혀 이전 활동 목록을 보여주는 중임을 알린다. 점수·비교는 서버가
          현재 스펙으로 재계산해 주므로 활동 목록만 이전 것이다. 이 안내가 없으면
          스펙을 바꾼 사용자가 "수정이 반영 안 된다"를 버그로 인지한다. */}
      {recMeta?.dailyLimitReached && (
        <div
          style={{
            marginBottom: 12,
            padding: "11px 14px",
            border: "1px solid #F0D8A8",
            borderRadius: 12,
            background: "#FFF9ED",
            color: "#79551F",
            fontSize: 12.5,
            lineHeight: 1.5,
          }}
        >
          오늘 추천 호출 횟수(3회)를 모두 사용했어요. 점수·비교는 방금 수정한 스펙 기준이지만, 아래 활동
          목록은 내일 첫 방문 때 새로 만들어져요.
        </div>
      )}

      {recLoading ? (
        <StateMessage variant="loading" title="AI가 추천을 준비하고 있어요…" />
      ) : recError ? (
        <StateMessage
          variant="error"
          title="추천을 불러오지 못했어요"
          description="네트워크를 확인한 뒤 잠시 후 다시 시도해주세요."
        />
      ) : recommendations.length === 0 ? (
        <StateMessage
          title="아직 추천할 활동이 없어요"
          description="스펙과 목표 직무를 입력하면 맞춤 활동을 추천해드려요."
        />
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {recommendations.map((r) => (
            <div
              key={r.id}
              onClick={() => onOpenDetail(r.id)}
              style={{
                background: "#fff",
                border: `1px solid ${LINE}`,
                borderRadius: 20,
                padding: "17px 18px",
                cursor: "pointer",
                transition: "transform .12s ease, box-shadow .12s ease",
              }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 12 }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 7, marginBottom: 8 }}>
                    <span style={{ fontSize: 11.5, fontWeight: 700, color: INK_MUTED, background: SURFACE_MUTED, padding: "4px 9px", borderRadius: 7 }}>
                      {r.type}
                    </span>
                    <span style={{ fontSize: 12, fontWeight: 600, color: ddayColor(r.deadline) }}>{dday(r.deadline)}</span>
                    {/* 비교 탭의 갭 이름 그대로 — "추천은 이 갭을 메우려고 나왔다"를 카드에서 바로 읽게 한다. */}
                    {r.targetGap && (
                      <span style={{ fontSize: 11.5, fontWeight: 700, color: BADGE.ok.color, background: BADGE.ok.bg, padding: "4px 9px", borderRadius: 7, whiteSpace: "nowrap" }}>
                        {r.targetGap} 갭 보완
                      </span>
                    )}
                  </div>
                  <div style={{ fontSize: 16.5, fontWeight: 700, color: INK, letterSpacing: "-0.01em", marginBottom: 3, lineHeight: 1.3 }}>
                    {r.name}
                  </div>
                  {/* 기관(org)은 백엔드 응답에 없어 목업에만 존재 → 있을 때만 표시 */}
                  {r.org && <div style={{ fontSize: 13, color: INK_FAINT, fontWeight: 500 }}>{r.org}</div>}
                </div>
                {/* 개별 활동 점수(score)도 목업 전용 → 있을 때만 표시 */}
                {r.score != null && (
                  <div style={{ textAlign: "right", flexShrink: 0 }}>
                    <div style={{ fontSize: 26, fontWeight: 800, color: PRIMARY, lineHeight: 1, letterSpacing: "-0.02em" }}>{r.score}</div>
                    <div style={{ fontSize: 11, fontWeight: 700, color: "#B0B0BA", marginTop: 2 }}>매치</div>
                  </div>
                )}
              </div>
              <div style={{ marginTop: 13, padding: "11px 13px", background: "#F8F7FC", borderRadius: 12, fontSize: 13, lineHeight: 1.5, color: "#4A4954" }}>
                <span style={{ color: PRIMARY, fontWeight: 700 }}>추천 이유 </span>
                {r.reason}
              </div>
              {/* E10-2(F-09) — 둘러보기(목업)는 실제 활동이 아니라 피드백을 받지 않는다. */}
              {!isDemo && typeof r.id === "string" && (
                <ReactionButtons
                  activityId={r.id}
                  current={reactionOverrides[r.id] ?? r.myReaction ?? null}
                  showNotice={feedbackNotice[r.id] === true}
                  onReact={handleReaction}
                />
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

interface ReactionButtonsProps {
  activityId: string;
  current: ReactionType | null;
  showNotice: boolean;
  onReact: (activityId: string, reaction: ReactionType) => void;
}

// 활동 카드의 👍/👎 — 현재 반응을 활성 표시하고, 같은 버튼을 다시 누르면 해제한다(handleReaction).
// 카드 자체에 onOpenDetail이 걸려 있어 클릭 전파를 막아야 한다.
function ReactionButtons({ activityId, current, showNotice, onReact }: ReactionButtonsProps) {
  const buttonStyle = (active: boolean, activeColor: string) => ({
    width: 34,
    height: 34,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 10,
    border: `1px solid ${active ? activeColor : LINE}`,
    background: active ? `color-mix(in srgb, ${activeColor} 14%, #fff)` : "#fff",
    fontSize: 15,
    lineHeight: 1,
    cursor: "pointer",
  });

  return (
    <div style={{ marginTop: 10, display: "flex", alignItems: "center", gap: 8 }} onClick={(e) => e.stopPropagation()}>
      <button
        type="button"
        aria-label="이 활동이 마음에 들어요"
        aria-pressed={current === "LIKE"}
        onClick={() => onReact(activityId, "LIKE")}
        style={buttonStyle(current === "LIKE", BADGE.ok.color)}
      >
        👍
      </button>
      <button
        type="button"
        aria-label="이 활동은 관심 없어요"
        aria-pressed={current === "DISLIKE"}
        onClick={() => onReact(activityId, "DISLIKE")}
        style={buttonStyle(current === "DISLIKE", BADGE.bad.color)}
      >
        👎
      </button>
      {showNotice && (
        <span style={{ fontSize: 11.5, color: INK_FAINT, lineHeight: 1.4 }}>다음 추천 갱신 때 반영돼요</span>
      )}
    </div>
  );
}
