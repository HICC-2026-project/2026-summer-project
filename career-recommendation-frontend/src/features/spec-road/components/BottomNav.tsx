"use client";

import { PRIMARY } from "../data";
import type { Tab } from "../types";

const NAV_ITEMS: { tab: Tab; label: string; icon: string }[] = [
  { tab: "home", label: "홈", icon: "◇" },
  { tab: "roadmap", label: "로드맵", icon: "⇢" },
  { tab: "compare", label: "비교", icon: "≡" },
  { tab: "profile", label: "프로필", icon: "○" },
];

interface BottomNavProps {
  active: Tab;
  onChange: (tab: Tab) => void;
}

export function BottomNav({ active, onChange }: BottomNavProps) {
  return (
    <div
      style={{
        position: "absolute",
        left: 0,
        right: 0,
        bottom: 0,
        height: "calc(72px + env(safe-area-inset-bottom))",
        paddingBottom: "env(safe-area-inset-bottom)",
        background: "rgba(255,255,255,0.94)",
        backdropFilter: "blur(12px)",
        borderTop: "1px solid #ECEBF1",
        display: "flex",
        alignItems: "stretch",
      }}
    >
      {NAV_ITEMS.map((n) => (
        <button
          key={n.tab}
          type="button"
          onClick={() => onChange(n.tab)}
          style={{
            flex: 1,
            border: "none",
            background: "none",
            cursor: "pointer",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            gap: 4,
            paddingTop: 8,
            color: active === n.tab ? PRIMARY : "#B0B0BA",
          }}
        >
          <span style={{ fontSize: 21, lineHeight: 1 }}>{n.icon}</span>
          <span style={{ fontSize: 11, fontWeight: 700 }}>{n.label}</span>
        </button>
      ))}
    </div>
  );
}
