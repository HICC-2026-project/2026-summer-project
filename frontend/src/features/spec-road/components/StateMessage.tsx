"use client";

// 탭에서 되풀이되는 로딩·오류·빈 결과 안내를 한 곳에서 그린다.
// (홈·로드맵이 같은 모양을 각자 인라인으로 갖고 있어 문구만 달랐다)
interface StateMessageProps {
  /** 로딩 중에는 카드 없이 문구만 보여준다. */
  variant?: "loading" | "empty" | "error";
  title: string;
  description?: string;
}

export function StateMessage({ variant = "empty", title, description }: StateMessageProps) {
  if (variant === "loading") {
    return (
      <div style={{ padding: "40px 0", textAlign: "center", color: "#9797A1", fontSize: 14, fontWeight: 500 }}>
        {title}
      </div>
    );
  }

  return (
    <div
      style={{
        padding: "32px 20px",
        textAlign: "center",
        background: "#fff",
        border: "1px solid #EDEDF2",
        borderRadius: 16,
      }}
    >
      <div
        style={{
          fontSize: 14.5,
          fontWeight: 700,
          color: variant === "error" ? "#E5484D" : "#15141B",
          marginBottom: description ? 6 : 0,
        }}
      >
        {title}
      </div>
      {description && <div style={{ fontSize: 13, color: "#61616C", lineHeight: 1.5 }}>{description}</div>}
    </div>
  );
}
