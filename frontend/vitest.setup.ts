import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// globals: false라 Testing Library의 자동 cleanup이 등록되지 않는다 — 테스트 간 DOM이 누적되지 않게 직접 건다.
afterEach(cleanup);
