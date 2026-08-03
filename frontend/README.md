# Spec Road — Frontend

**Spec Road**의 Next.js(App Router) + Tailwind + shadcn/ui 기반 프론트엔드입니다.

## 기술 스택
- Next.js 16 (App Router) / React 19 / TypeScript
- Tailwind CSS v4 / shadcn/ui
- 백엔드: `backend` (Spring Boot, 기본 `http://localhost:8080`)

## 로컬 실행

```bash
npm install

# 환경변수 설정
cp .env.local.example .env.local
# 필요 시 NEXT_PUBLIC_API_BASE_URL 수정

npm run dev
```

http://localhost:3000 에서 확인. 백엔드(`backend`)를 먼저 띄워둬야 API 연동 화면이 정상 동작합니다.

## 배포 (Vercel)

`main` 브랜치에 머지되면 Vercel이 자동 배포합니다 (Root Directory: `frontend`).

배포 환경은 HTTPS인데 백엔드(EC2)는 HTTP라, 브라우저가 직접 호출하면 Mixed Content로 차단됩니다.
그래서 `next.config.ts`의 rewrites로 `/api/*` 요청을 Vercel 서버가 백엔드에 대신 전달합니다.

백엔드 주소를 바꿀 때(도메인 구매·HTTPS 전환 등)는 코드 수정 대신
Vercel 대시보드 → Settings → Environment Variables 에서 아래 값을 설정합니다.

| 변수 | 용도 |
|---|---|
| `BACKEND_ORIGIN` | Vercel 서버가 `/api/*`를 전달할 백엔드 주소 (rewrites) |
| `NEXT_PUBLIC_BACKEND_ORIGIN` | 카카오 로그인 진입 시 이동할 백엔드 주소 |
| `NEXT_PUBLIC_API_BASE_URL` | 브라우저가 API를 호출할 주소 (미설정 시 프록시 사용) |

값을 지정하지 않으면 현재 배포된 EC2 주소를 기본값으로 사용합니다. 자세한 설명은 `.env.local.example` 참고.

## 구조
- `src/app` — 페이지 (App Router)
- `src/components/ui` — shadcn/ui 컴포넌트
- `src/lib/api.ts` — 백엔드 API 호출 공통 fetch 래퍼

## 디자인 반영
디자인은 별도 AI 디자인 툴로 작업 후 전달받아 반영합니다. 컴포넌트는 `npx shadcn@latest add <컴포넌트명>` 으로 필요할 때마다 추가합니다.
