"use client";

import { DEMO_SPEC_POSITION, DEMO_USER_NAME, PRIMARY } from "../../data";
import { StateMessage } from "../../components/StateMessage";
import { dday, ddayColor, hasMeaningfulLangScore, jobLabel } from "../../helpers";
import type { Recommendation, RecommendationMeta, Spec, Target } from "../../types";

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

export function HomeTab({ spec, target, nickname, isDemo, recommendations, recMeta, recLoading, recError, onOpenDetail }: HomeTabProps) {
  const targetSummary = `${target.size} ${jobLabel(target.job)}`;
  const displayName = nickname ?? DEMO_USER_NAME;

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
          <p style={{ fontSize: 14, color: "#61616C", margin: "0 0 4px", fontWeight: 500 }}>{displayName}님, 안녕하세요 👋</p>
          <h1 style={{ fontSize: 23, fontWeight: 800, letterSpacing: "-0.02em", margin: 0, color: "#15141B", lineHeight: 1.3 }}>
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
              <span
                key={a.axis}
                style={{
                  fontSize: 12.5,
                  fontWeight: 700,
                  background: "rgba(255,255,255,0.16)",
                  padding: "6px 11px",
                  borderRadius: 999,
                  whiteSpace: "nowrap",
                }}
              >
                {a.label}{" "}
                {a.percentile == null
                  ? "미입력"
                  : a.percentile >= 100
                    ? "최상위"
                    : `상위 ${100 - a.percentile}%`}
              </span>
            ))}
            {position!.gaps.length > 0 && (
              <span style={{ fontSize: 12.5, fontWeight: 700, background: "rgba(255,255,255,0.16)", padding: "6px 11px", borderRadius: 999, whiteSpace: "nowrap" }}>
                보완 추천 {position!.gaps.length}개
              </span>
            )}
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
            <div style={{ flex: 1, background: "#fff", border: "1px solid #EDEDF2", borderRadius: 16, padding: 14 }}>
              <div style={{ fontSize: 12, color: "#9797A1", fontWeight: 600, marginBottom: 6 }}>강점</div>
              <div style={{ fontSize: 15, fontWeight: 700, color: "#12A150" }}>학점 · 자격증</div>
            </div>
            <div style={{ flex: 1, background: "#fff", border: "1px solid #EDEDF2", borderRadius: 16, padding: 14 }}>
              <div style={{ fontSize: 12, color: "#9797A1", fontWeight: 600, marginBottom: 6 }}>보완 필요</div>
              <div style={{ fontSize: 15, fontWeight: 700, color: "#E5484D" }}>어학 · 실무경험</div>
            </div>
          </>
        ) : (
          <>
            <div style={{ flex: 1, background: "#fff", border: "1px solid #EDEDF2", borderRadius: 16, padding: 14 }}>
              <div style={{ fontSize: 12, color: "#9797A1", fontWeight: 600, marginBottom: 6 }}>내 학점</div>
              <div style={{ fontSize: 15, fontWeight: 700, color: "#15141B" }}>
                {spec.gpa ? `${spec.gpa} / ${spec.gpaScale}` : "미입력"}
              </div>
            </div>
            <div style={{ flex: 1, background: "#fff", border: "1px solid #EDEDF2", borderRadius: 16, padding: 14 }}>
              <div style={{ fontSize: 12, color: "#9797A1", fontWeight: 600, marginBottom: 6 }}>어학 · 자격증</div>
              <div style={{ fontSize: 15, fontWeight: 700, color: "#15141B" }}>{specSummary}</div>
            </div>
          </>
        )}
      </div>

      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 14 }}>
        <h2 style={{ fontSize: 18, fontWeight: 800, letterSpacing: "-0.02em", margin: 0, color: "#15141B" }}>
          지금 지원하면 좋은 활동
        </h2>
        <span
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 4,
            fontSize: 11.5,
            fontWeight: 700,
            color: isFallbackRec ? "#9797A1" : PRIMARY,
            background: isFallbackRec ? "#F1F0F6" : `color-mix(in srgb, ${PRIMARY} 11%, #fff)`,
            padding: "5px 9px",
            borderRadius: 999,
          }}
        >
          {isFallbackRec ? "일반 추천" : "✦ AI 추천"}
        </span>
      </div>

      {/* 추천 갱신 정책 안내: 스펙을 바꿔야 새로 생성되고, 하루 3회까지만 가능하다. 로그인 유저에게만 표시. */}
      {recMeta != null && (
        <p style={{ fontSize: 12, color: "#9797A1", margin: "0 0 12px", lineHeight: 1.5 }}>
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
                border: "1px solid #EDEDF2",
                borderRadius: 20,
                padding: "17px 18px",
                cursor: "pointer",
                transition: "transform .12s ease, box-shadow .12s ease",
              }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 12 }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 7, marginBottom: 8 }}>
                    <span style={{ fontSize: 11.5, fontWeight: 700, color: "#61616C", background: "#F1F0F6", padding: "4px 9px", borderRadius: 7 }}>
                      {r.type}
                    </span>
                    <span style={{ fontSize: 12, fontWeight: 600, color: ddayColor(r.deadline) }}>{dday(r.deadline)}</span>
                  </div>
                  <div style={{ fontSize: 16.5, fontWeight: 700, color: "#15141B", letterSpacing: "-0.01em", marginBottom: 3, lineHeight: 1.3 }}>
                    {r.name}
                  </div>
                  {/* 기관(org)은 백엔드 응답에 없어 목업에만 존재 → 있을 때만 표시 */}
                  {r.org && <div style={{ fontSize: 13, color: "#9797A1", fontWeight: 500 }}>{r.org}</div>}
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
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
