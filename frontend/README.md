# Spec Road — Frontend

**Spec Road**의 Next.js(App Router) + Tailwind + shadcn/ui 기반 프론트엔드입니다.

## 기술 스택
- Next.js 16 (App Router) / React 19 / TypeScript
- Tailwind CSS v4 / shadcn/ui
- 백엔드: `career-recommendation-service` (Spring Boot, 기본 `http://localhost:8080`)

## 로컬 실행

```bash
npm install

# 환경변수 설정
cp .env.local.example .env.local
# 필요 시 NEXT_PUBLIC_API_BASE_URL 수정

npm run dev
```

http://localhost:3000 에서 확인. 백엔드(`career-recommendation-service`)를 먼저 띄워둬야 API 연동 화면이 정상 동작합니다.

## 구조
- `src/app` — 페이지 (App Router)
- `src/components/ui` — shadcn/ui 컴포넌트
- `src/lib/api.ts` — 백엔드 API 호출 공통 fetch 래퍼

## 디자인 반영
디자인은 별도 AI 디자인 툴로 작업 후 전달받아 반영합니다. 컴포넌트는 `npx shadcn@latest add <컴포넌트명>` 으로 필요할 때마다 추가합니다.
