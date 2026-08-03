"use client";

import { BottomNav } from "../components/BottomNav";
import type { Recommendation, RecommendationMeta, RoadmapMilestone, Spec, Tab, Target } from "../types";
import { CompareTab } from "./tabs/CompareTab";
import { HomeTab } from "./tabs/HomeTab";
import { ProfileTab } from "./tabs/ProfileTab";
import { RoadmapTab } from "./tabs/RoadmapTab";

interface AppScreenProps {
  tab: Tab;
  onTabChange: (tab: Tab) => void;
  spec: Spec;
  target: Target;
  nickname: string | null;
  recommendations: Recommendation[];
  recMeta: RecommendationMeta | null;
  recLoading: boolean;
  recError: boolean;
  roadmap: RoadmapMilestone[];
  roadmapLoading: boolean;
  roadmapError: boolean;
  isDemo: boolean;
  onOpenDetail: (id: string | number) => void;
  onEditSpec: () => void;
  onLogout: () => void;
}

export function AppScreen({
  tab,
  onTabChange,
  spec,
  target,
  nickname,
  recommendations,
  recMeta,
  recLoading,
  recError,
  roadmap,
  roadmapLoading,
  roadmapError,
  isDemo,
  onOpenDetail,
  onEditSpec,
  onLogout,
}: AppScreenProps) {
  return (
    <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", background: "#F6F6F9" }}>
      <div className="cf-scroll" style={{ flex: 1, overflowY: "auto", overflowX: "hidden" }}>
        {tab === "home" && (
          <HomeTab
            target={target}
            nickname={nickname}
            recommendations={recommendations}
            recMeta={recMeta}
            recLoading={recLoading}
            recError={recError}
            onOpenDetail={onOpenDetail}
          />
        )}
        {tab === "roadmap" && (
          <RoadmapTab target={target} roadmap={roadmap} roadmapLoading={roadmapLoading} roadmapError={roadmapError} />
        )}
        {tab === "compare" && <CompareTab />}
        {tab === "profile" && (
          <ProfileTab
            spec={spec}
            target={target}
            nickname={nickname}
            isDemo={isDemo}
            onEditSpec={onEditSpec}
            onLogout={onLogout}
          />
        )}
      </div>
      <BottomNav active={tab} onChange={onTabChange} />
    </div>
  );
}
