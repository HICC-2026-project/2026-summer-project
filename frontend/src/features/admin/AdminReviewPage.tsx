"use client";

import { useEffect, useState, type CSSProperties } from "react";
import Link from "next/link";
import { ApiError } from "@/lib/api";
import { getAccessToken } from "@/lib/auth";
import { BADGE, PRIMARY } from "@/features/spec-road/data";
import { StateMessage } from "@/features/spec-road/components/StateMessage";
import {
  fetchProofObjectUrl,
  getAdminReports,
  reviewReport,
  type AdminPasserReport,
  type PageResponse,
  type ReviewAction,
  type ReviewStatus,
} from "./api";

// 합격자 제보 검수 화면 (팀 내부용). 기능 최소: 상태별 목록 → 증빙 보기 → 승인/반려.
// 권한은 서버가 판정한다. 여기서는 403을 "권한 없음" 안내로 바꿀 뿐이다.

const STATUS_TABS: { key: ReviewStatus; label: string }[] = [
  { key: "PENDING", label: "검수 대기" },
  { key: "VERIFIED", label: "반영 완료" },
  { key: "REJECTED", label: "반려" },
];

const ERROR_TEXT = {
  unauthenticated: { title: "로그인이 필요해요", description: "앱에서 카카오 로그인 후 다시 열어 주세요." },
  forbidden: { title: "검수 권한이 없어요", description: "관리자 계정(ADMIN_PROVIDER_IDS)만 이 화면을 쓸 수 있어요. 권한이 추가됐다면 로그아웃 후 다시 로그인해 주세요." },
  failed: { title: "목록을 불러오지 못했어요", description: "잠시 후 다시 시도해 주세요." },
} as const;

const CELL: CSSProperties = { padding: "10px 12px" };
const CELL_NOWRAP: CSSProperties = { ...CELL, whiteSpace: "nowrap" };

function actionButton(kind: "approve" | "reject", disabled: boolean): CSSProperties {
  const approve = kind === "approve";
  return {
    flex: 1,
    height: 42,
    border: approve ? "none" : `1px solid ${BADGE.bad.color}`,
    borderRadius: 11,
    background: approve ? BADGE.ok.color : "#fff",
    color: approve ? "#fff" : BADGE.bad.color,
    fontWeight: 700,
    fontSize: 14,
    cursor: "pointer",
    opacity: disabled ? 0.5 : 1,
  };
}

function formatLang(scores: AdminPasserReport["languageScores"]): string {
  if (!scores || scores.length === 0) return "없음";
  return scores
    .map((s) => `${s.type ?? "?"} ${s.grade ?? s.score ?? ""}`.trim())
    .join(", ");
}

export function AdminReviewPage() {
  const [status, setStatus] = useState<ReviewStatus>("PENDING");
  const [page, setPage] = useState(0);
  // data === null이 "불러오는 중". 탭·페이지 전환 핸들러에서 null로 되돌리고, effect는 응답이 온 뒤에만 상태를 만진다.
  const [data, setData] = useState<PageResponse<AdminPasserReport> | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [error, setError] = useState<keyof typeof ERROR_TEXT | null>(null);
  const [selected, setSelected] = useState<AdminPasserReport | null>(null);

  useEffect(() => {
    let cancelled = false;
    const run = async () => {
      if (!getAccessToken()) {
        setError("unauthenticated");
        return;
      }
      try {
        const res = await getAdminReports(status, page);
        if (!cancelled) {
          setError(null);
          setData(res);
        }
      } catch (e) {
        if (cancelled) return;
        if (e instanceof ApiError && e.status === 403) setError("forbidden");
        else if (e instanceof ApiError && e.status === 401) setError("unauthenticated");
        else setError("failed");
      }
    };
    void run();
    return () => {
      cancelled = true;
    };
  }, [status, page, reloadKey]);

  const loading = data === null && error === null;
  const items = data?.content ?? [];
  const total = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 0;
  // 목록을 바꾸는 모든 동작은 여기로 — data를 null로 되돌려 "불러오는 중"을 만들고 effect를 다시 돌린다.
  const go = (next: { status?: ReviewStatus; page?: number; refetch?: boolean }) => {
    setData(null);
    setSelected(null);
    if (next.status !== undefined) {
      setStatus(next.status);
      setPage(0);
    }
    if (next.page !== undefined) setPage(next.page);
    if (next.refetch) setReloadKey((k) => k + 1);
  };

  // 상태가 바뀌면 현재 탭 목록에서 빠진다(대기 → 반영/반려). 목록을 다시 받는 게 가장 단순하다.
  const onReviewed = () => go({ refetch: true });

  return (
    <div style={{ minHeight: "100dvh", background: "#F6F6F9", color: "#15141B", fontFamily: "inherit" }}>
      <header style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "16px 24px", background: "#fff", borderBottom: "1px solid #EDEDF2" }}>
        <div>
          <div style={{ fontSize: 18, fontWeight: 800 }}>합격자 제보 검수</div>
          <div style={{ fontSize: 12, color: "#9797A1" }}>승인하면 즉시 비교 데이터에 반영돼요. 반려는 데이터를 보존하고 사유만 남겨요.</div>
        </div>
        <Link href="/" style={{ fontSize: 13, color: PRIMARY, fontWeight: 700, textDecoration: "none" }}>
          ← 앱으로
        </Link>
      </header>

      <main style={{ maxWidth: 1080, margin: "0 auto", padding: "20px 24px 60px" }}>
        <div style={{ display: "flex", gap: 8, marginBottom: 16 }}>
          {STATUS_TABS.map((t) => {
            const active = t.key === status;
            return (
              <button
                key={t.key}
                type="button"
                onClick={() => go({ status: t.key })}
                style={{
                  padding: "8px 14px",
                  borderRadius: 999,
                  border: `1px solid ${active ? PRIMARY : "#E1E0EA"}`,
                  background: active ? PRIMARY : "#fff",
                  color: active ? "#fff" : "#4A4954",
                  fontSize: 13,
                  fontWeight: 700,
                  cursor: "pointer",
                }}
              >
                {t.label}
                {active && !loading && !error ? ` ${total}` : ""}
              </button>
            );
          })}
        </div>

        {error && <StateMessage variant="error" title={ERROR_TEXT[error].title} description={ERROR_TEXT[error].description} />}

        {!error && (
          <div style={{ display: "grid", gridTemplateColumns: selected ? "minmax(0, 1fr) minmax(320px, 420px)" : "1fr", gap: 16, alignItems: "start" }}>
            <div style={{ background: "#fff", border: "1px solid #EDEDF2", borderRadius: 16, overflow: "hidden" }}>
              {loading || items.length === 0 ? (
                <div style={{ padding: 40, textAlign: "center", color: "#9797A1", fontSize: 14 }}>{loading ? "불러오는 중…" : "이 상태의 제보가 없어요."}</div>
              ) : (
                <div style={{ overflowX: "auto" }}>
                  <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
                    <thead>
                      <tr style={{ background: "#FAFAFC", color: "#9797A1", textAlign: "left" }}>
                        {["직무", "연도", "학점", "어학", "자격증", "경험", "제보일", "증빙"].map((h) => (
                          <th key={h} style={{ ...CELL_NOWRAP, fontWeight: 600 }}>{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {items.map((r) => {
                        const isSel = selected?.reportId === r.reportId;
                        return (
                          <tr
                            key={r.reportId}
                            onClick={() => setSelected(r)}
                            style={{ cursor: "pointer", background: isSel ? `color-mix(in srgb, ${PRIMARY} 8%, #fff)` : "transparent", borderTop: "1px solid #F1F0F6" }}
                          >
                            <td style={{ ...CELL_NOWRAP, fontWeight: 700 }}>{r.jobTypeLabel}</td>
                            <td style={CELL}>{r.year}</td>
                            <td style={CELL_NOWRAP}>{r.gpa ?? "-"} / {r.gpaMax ?? "-"}</td>
                            <td style={CELL}>{formatLang(r.languageScores)}</td>
                            <td style={CELL}>{r.certifications.length ? r.certifications.join(", ") : "없음"}</td>
                            <td style={CELL}>{r.experienceCount ?? 0}개</td>
                            <td style={{ ...CELL_NOWRAP, color: "#61616C" }}>{r.createdAt.slice(0, 10)}</td>
                            <td style={CELL_NOWRAP}>{r.proof ? "있음" : <span style={{ color: BADGE.bad.color }}>없음</span>}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
              {totalPages > 1 && (
                <div style={{ display: "flex", justifyContent: "center", gap: 8, padding: 12, borderTop: "1px solid #F1F0F6" }}>
                  <PageButton label="이전" disabled={page === 0} onClick={() => go({ page: page - 1 })} />
                  <span style={{ fontSize: 12, color: "#61616C", alignSelf: "center" }}>{page + 1} / {totalPages}</span>
                  <PageButton label="다음" disabled={page + 1 >= totalPages} onClick={() => go({ page: page + 1 })} />
                </div>
              )}
            </div>

            {selected && <DetailPanel key={selected.reportId} report={selected} onClose={() => setSelected(null)} onReviewed={onReviewed} />}
          </div>
        )}
      </main>
    </div>
  );
}

function DetailPanel({ report, onClose, onReviewed }: { report: AdminPasserReport; onClose: () => void; onReviewed: () => void }) {
  const [proofUrl, setProofUrl] = useState<string | null>(null);
  const [proofState, setProofState] = useState<"loading" | "ready" | "missing">(report.proof ? "loading" : "missing");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  // 패널은 key=reportId로 리마운트되므로 상태 초기화는 useState 초기값이 맡는다.
  useEffect(() => {
    let url: string | null = null;
    let cancelled = false;
    if (!report.proof) {
      return;
    }
    fetchProofObjectUrl(report.reportId).then((u) => {
      if (cancelled) {
        if (u) URL.revokeObjectURL(u);
        return;
      }
      url = u;
      setProofUrl(u);
      setProofState(u ? "ready" : "missing");
    });
    return () => {
      cancelled = true;
      if (url) URL.revokeObjectURL(url);
    };
  }, [report.reportId, report.proof]);

  const act = async (action: ReviewAction) => {
    if (action === "REJECT" && reason.trim() === "") {
      setErr("반려 사유를 적어 주세요.");
      return;
    }
    setBusy(true);
    setErr(null);
    try {
      await reviewReport(report.reportId, action, action === "REJECT" ? reason.trim() : undefined);
      onReviewed();
    } catch (e) {
      setErr(e instanceof ApiError ? e.message : "처리하지 못했어요.");
    } finally {
      setBusy(false);
    }
  };

  const pending = report.status === "PENDING";
  const approveDisabled = busy || report.status === "VERIFIED";
  const rejectDisabled = busy || report.status === "REJECTED";

  return (
    <aside style={{ background: "#fff", border: "1px solid #EDEDF2", borderRadius: 16, padding: 18, position: "sticky", top: 16 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <div style={{ fontSize: 15, fontWeight: 800 }}>{report.jobTypeLabel} · {report.year}년</div>
        <button type="button" onClick={onClose} style={{ border: "none", background: "transparent", color: "#9797A1", cursor: "pointer", fontSize: 13 }}>닫기</button>
      </div>

      <div style={{ fontSize: 12, color: "#9797A1", marginBottom: 6 }}>증빙 {report.proof?.originalName ? `· ${report.proof.originalName}` : ""}</div>
      <div style={{ border: "1px solid #EDEDF2", borderRadius: 12, minHeight: 160, display: "flex", alignItems: "center", justifyContent: "center", overflow: "hidden", background: "#FAFAFC", marginBottom: 14 }}>
        {proofState === "loading" && <span style={{ fontSize: 13, color: "#9797A1" }}>증빙 불러오는 중…</span>}
        {proofState === "missing" && <span style={{ fontSize: 13, color: BADGE.bad.color }}>증빙 파일이 없어요 (서버 재배포로 유실됐을 수 있어요)</span>}
        {proofState === "ready" && proofUrl && (
          <a href={proofUrl} target="_blank" rel="noreferrer" style={{ display: "block", width: "100%" }}>
            {/* object URL이라 next/image 최적화를 탈 수 없다 */}
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={proofUrl} alt="합격 증빙" style={{ width: "100%", display: "block" }} />
          </a>
        )}
      </div>

      {report.status !== "PENDING" && (
        <div style={{ fontSize: 12.5, color: "#61616C", marginBottom: 12, lineHeight: 1.5 }}>
          {report.status === "VERIFIED" ? "반영 완료" : "반려"} · {report.reviewedAt?.slice(0, 16).replace("T", " ")}
          {report.rejectReason && <div>사유: {report.rejectReason}</div>}
        </div>
      )}

      <textarea
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        placeholder="반려 사유 (반려할 때만 필수, 300자 이내)"
        maxLength={300}
        rows={3}
        style={{ width: "100%", boxSizing: "border-box", border: "1px solid #E1E0EA", borderRadius: 10, padding: 10, fontSize: 13, resize: "vertical", marginBottom: 10 }}
      />
      {err && <div style={{ fontSize: 12.5, color: BADGE.bad.color, marginBottom: 8 }}>{err}</div>}
      <div style={{ display: "flex", gap: 8 }}>
        <button type="button" disabled={approveDisabled} onClick={() => act("APPROVE")} style={actionButton("approve", approveDisabled)}>
          {pending ? "승인" : "승인으로 변경"}
        </button>
        <button type="button" disabled={rejectDisabled} onClick={() => act("REJECT")} style={actionButton("reject", rejectDisabled)}>
          {pending ? "반려" : "반려로 변경"}
        </button>
      </div>
    </aside>
  );
}

function PageButton({ label, disabled, onClick }: { label: string; disabled: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      style={{ padding: "6px 12px", borderRadius: 8, border: "1px solid #E1E0EA", background: "#fff", fontSize: 12, cursor: disabled ? "default" : "pointer", opacity: disabled ? 0.4 : 1 }}
    >
      {label}
    </button>
  );
}
