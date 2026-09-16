import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "node:path";

// 컴포넌트 단위 테스트(happy-dom — jsdom은 Node 20.17에서 require(esm) 오류). Next 런타임 없이 React만 올려 탭 컴포넌트의 렌더 규칙을 고정한다.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: "happy-dom",
    include: ["src/**/*.test.{ts,tsx}"],
    setupFiles: ["./vitest.setup.ts"],
  },
  resolve: {
    alias: { "@": path.resolve(__dirname, "src") },
  },
});
