"use client";

import type { CSSProperties, ReactNode } from "react";
import { chipStyle } from "../helpers";

interface ChipProps {
  selected: boolean;
  onClick: () => void;
  children: ReactNode;
  style?: CSSProperties;
}

export function Chip({ selected, onClick, children, style }: ChipProps) {
  const { background, color, borderColor } = chipStyle(selected);
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        height: 38,
        padding: "0 15px",
        borderRadius: 999,
        fontSize: 13.5,
        fontWeight: 600,
        cursor: "pointer",
        border: `1px solid ${borderColor}`,
        background,
        color,
        transition: "all .15s ease",
        ...style,
      }}
    >
      {children}
    </button>
  );
}
