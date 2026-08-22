# Spec Road 기능 백로그 (v9 위치·갭 체계 이후)

기준 커밋: `306657c` (PR #53 머지, 2026-08-22).
목적: "현재 스펙 → 목표 직무 합격자 분포 내 위치·갭 → 활동 추천·로드맵" 루프를 완성하고, 데이터가 실제로 축적되는 구조를 만든다.

표기
- 담당: BE-1(AI/추천) · BE-2(DB/데이터) · BE-3(API/인프라) · FE
- 규모: S(반나절) · M(1~2일) · L(3일+)
- 의존: 선행 작업 번호

---

## 직무 체계 (노션 · 코드 대조)

출처: 노션 [2026-07-14 회의 안건](https://app.notion.com/p/39c829124dbd818eb673de89e49ab217) 안건 6, [기능 명세서](https://app.notion.com/p/38a829124dbd8155b3e4ce06ef95ac29) F-02, `V13__normalize_passer_data.sql`.

| 코드 | FE 라벨 | 비고 |
|---|---|---|
| `BACKEND` | 백엔드 | 레거시 `BE` → V13 정규화 |
| `FRONTEND` | 프론트엔드 | 레거시 `FE` |
| `DATA_ENGINEER` | 데이터 엔지니어 | 레거시 `데이터`, `데이터 엔지니어` |
| `AI_ML` | AI/ML | 레거시 `AI/ML` |
| `PM` | 기획/PM | |
| `SECURITY` | 보안 | 레거시 `보안` |

- **인프라/DevOps 직무는 없다.** 목표 직무로 선택 불가하므로 GitHub 추론(E3)도 이 6종으로만 분류한다. 인프라 신호(Dockerfile·workflows·terraform)는 별도 직무가 아니라 BACKEND 보조 신호로 취급. 7번째 직무 추가는 팀 결정 사항.
- 현재 코드에 직무 enum이 없고 문자열이 4곳에 흩어져 있다: `TargetJobRequest` regex, `PasserReportRequest` regex(대소문자 무시), `PasserData` 주석, FE `types.ts JobCode`/`data.ts JOB_OPTIONS`. → **E0 선행 작업으로 단일화**.
- 합격자 `experienceCount`는 직무 구분이 없다(직무별 경험 개수가 아님).

## E0. 직무 enum 단일화 — 담당 BE-3, 규모 S (모든 에픽의 선행)

- [x] `JobType` enum (`BACKEND, FRONTEND, DATA_ENGINEER, AI_ML, PM, SECURITY`) + 한글 라벨 + `from(String)` (V13 이후 레거시 별칭 불필요 → 거부)
- [x] `TargetJobRequest`·`PasserReportRequest` regex → `@ValidJobType` 커스텀 validator
- [x] `PasserData.jobType`, `TargetJob.jobType`은 문자열 컬럼 유지하되 서비스에서 `JobType.of(...).name()`으로 정규화 저장
- [x] `GET /api/v1/jobs` (permitAll) — FE `JOB_OPTIONS` 교체는 미적용(선택)
- [x] `JobTypeTest` — 레거시 별칭·라벨 거부, `labelOf` 미지 코드 원문 반환. basisMessage는 한글 라벨 사용

---

## 우선순위 요약

| 순위 | 에픽 | 이유 |
|---|---|---|
| 1 | E1 경험 축 완성 | 코드가 자리를 비워둔 상태(`SpecPositionCalculator` EXPERIENCE 축 `percentile(null)`). 4축 중 1축이 영구 미입력 |
| 2 | E2 제보 검수 파이프라인 | 제보 → 검수 → 프로필 갱신 루프가 끊겨 있음 (`POST`만 존재, `@CacheEvict` 호출자 없음) |
| 3 | E4 데이터 부족 UX | 표본 3명 미만 직무가 많을수록 제보 유도가 데이터 루프의 입구 |
| 4 | E3 GitHub 경험 추론 (OAuth `repo`) | E1 위에 얹는 차별화 기능. 외부 API·레이트리밋 리스크 있어 E1 뒤 |
| 5 | E11 경험 심화 (기여 프로필·커버리지·외부 소스) | "같은 프로젝트라도 무엇을 했는가"를 구분. 점수화 없이 커버리지·추천에만 사용 |
| 6 | E5 갭 ↔ 추천 정합성 | v9 약속("두 화면이 같은 갭을 본다")의 검증. E11-2 프롬프트 반영과 합쳐 진행 |
| 7 | E6~E10 | 운영·품질·확장·보류 기능 |

---

## E1. 경험(EXPERIENCE) 축 완성  — 담당 BE-2/BE-3/FE, 규모 M

현재: `PasserData.experienceCount`는 있음. `UserSpec`에는 경험 필드 없음. calculator는 합격자 중앙값만 보여주고 사용자 쪽은 항상 "미입력".
**결정 번복 필요**: 노션 7/14 회의 안건 1에서 "경험은 스펙으로 저장하지 않고 합격자 경험을 보여주는 방식 + 로드맵(F-05)으로 대체"로 확정했고, 기능 명세서 F-04에도 "경험 항목 제외, 가중치 25%를 나머지에 분배"로 기록돼 있다. v9에서 가중 총점이 사라지고 합격자 경험 중앙값을 이미 보여주고 있으므로 "사용자 쪽만 입력받자"는 제안이지만, 회의록·기능 명세서 갱신이 선행돼야 한다. 하위 선택지: 정수 카운트 vs 항목 리스트.

### E1-1. 스키마 (BE-2)
- [ ] `V10__add_user_spec_experiences.sql` — `user_specs.experiences JSONB DEFAULT '[]'` (**리스트 구조 확정**, 정수 카운트 옵션 폐기). 카운트는 리스트 길이로 파생
- [ ] 항목 스키마 (E11 기여 프로필과 공유):
  ```
  { id, type: INTERNSHIP|PROJECT|COMPETITION|CLUB|BOOTCAMP|OTHER,
    title, months, jobType?,
    role: "한 줄",                      // 사용자 수정 가능
    areas: [AUTH, API, DB, CI_CD, TEST, UI, DATA_PIPELINE, ML_MODEL, ...],
    stack: ["Spring Security", ...],
    evidence: { kind: GITHUB|LINK|SELF, repo?, commits?, contributionRatio?, topPaths?[] },
    verification: GITHUB_VERIFIED|LINK_PROVIDED|SELF_REPORT,
    source: MANUAL|GITHUB|ACTIVITY_MATCH }
  ```
- [ ] "경험 1개로 세는 기준" 문서화 (인턴십·공모전·대외활동·결과물 있는 프로젝트·학회/동아리 활동. 수강 과제·튜토리얼 제외) — 제보 폼과 온보딩에 동일 문구
- [ ] `UserSpec` 엔티티 필드 추가 + `updatedAt` 갱신 확인
- [ ] **가중치 없음 원칙**: EXPERIENCE 축은 개수만 percentile. `areas`/`stack`/`verification`은 점수에 반영하지 않고 E11 커버리지·추천 프롬프트에만 사용

### E1-2. API (BE-3)
- [ ] `UserSpecRequest`에 `experienceCount` / `experiences` 추가, `@Min(0)`, 리스트 최대 길이(예: 30) 검증
- [ ] `UserSpecResponse`, `UserMeResponse` 반영
- [ ] `UserControllerValidationTest`에 경계값 케이스 추가
- [ ] Swagger 설명 갱신

### E1-3. 계산 (BE-1)
- [ ] `SpecPositionCalculator` EXPERIENCE 축: `percentile(null)` → `percentileOf(expCounts, userExp)`, `myValue` = "N개"
- [ ] 사용자 0개는 "미입력"이 아니라 **0개로 percentile 계산**할지 결정 (GPA/TOEIC은 0 → 미입력 처리 중. 경험은 "없음"이 유효한 값이므로 `null`일 때만 미입력 권장)
- [ ] `SpecPositionCalculatorTest`: 경험 있음/0개/null/동률 케이스
- [ ] `PromptDataBuilder`에 경험 리스트가 Gemini 프롬프트로 들어가도록 연결 + 테스트
- [ ] `OldVsNewScoreComparisonDemo` 갱신 또는 삭제

### E1-4. 프론트 (FE)
- [ ] `types.ts` Spec에 experiences 추가, `api.ts putSpec` 직렬화
- [ ] `OnboardingScreen` 경험 입력 스텝 (항목 추가/삭제, 유형 선택, 개월 수)
- [ ] `ProfileTab` 수정 UI
- [ ] `CompareTab` 경험 막대가 실제로 그려지는지 확인 (현재 미입력이면 막대 없음 로직 그대로 동작)
- [ ] 로컬 캐시(`recommendationCache.ts`)가 스펙 변경 시 무효화되는지 확인

---

## E2. 합격자 제보 검수 파이프라인 — 담당 BE-3(인가/API) · BE-2(데이터), 규모 M

현재: `POST /api/v1/passers/reports` → `is_verified=false`, `data_origin=USER_REPORT`. 검수 전환 경로 없음. `User.role`은 항상 `USER`. `JobSpecProfileService.evictAll()` 류 `@CacheEvict`는 준비돼 있으나 호출자 없음. 증빙은 로컬 FS.

### E2-1. 관리자 권한 (BE-3)
- [ ] `User.role`에 `ADMIN` 도입. 부여 방식 결정: (a) `V11` 마이그레이션으로 특정 providerId 지정 (b) 환경변수 `ADMIN_PROVIDER_IDS` 로 기동 시 승격 → **(b) 권장** (시드에 개인 식별자 안 남김)
- [ ] `JwtTokenProvider` 클레임에 role 포함 여부 확인, `JwtAuthenticationFilter`가 `ROLE_ADMIN` 권한을 세팅하는지 확인
- [ ] `SecurityConfig`: `/api/v1/admin/**` → `hasRole("ADMIN")`
- [ ] 403 응답 포맷 `GlobalExceptionHandler`/`JwtAccessDeniedHandler` 일관성 테스트

### E2-2. 검수 API (BE-3)
- [ ] `GET /api/v1/admin/passers/reports?status=PENDING&page=` — 미검수 목록 (증빙 메타 포함)
- [ ] `GET /api/v1/admin/passers/reports/{id}/proof` — 증빙 이미지 스트리밍 (관리자만)
- [ ] `PATCH /api/v1/admin/passers/reports/{id}` — `{action: APPROVE|REJECT, reason?}`
  - APPROVE: `is_verified=true`, 검수자/시각 기록 → `JobSpecProfileService` 캐시 무효화 호출
  - REJECT: `is_verified=false` 유지 + `rejected_at`, `reject_reason` (삭제하지 않음)
- [ ] `V11__passer_review_columns.sql` — `reviewed_at`, `reviewed_by`, `reject_reason`
- [ ] 승인 시 증빙 파일 보존/삭제 정책 결정 (개인정보 최소 보관: 승인 후 N일 뒤 삭제 권장)
- [ ] 컨트롤러 검증 테스트 + 서비스 테스트(승인 → 캐시 evict 호출 검증)

### E2-3. 제보 품질 (BE-2)
- [ ] 제보 시 `SpecNormalizer`로 자격증 문자열 정규화 저장 (현재 집계 시 정규화인지 확인)
- [ ] 동일 사용자 중복 제보 제한 (활동+직무 기준 1일 1회 등)
- [ ] `specSummary`에 개인정보 패턴(이메일/전화/학교명) 차단 검증 추가

### E2-4. 증빙 저장소 전환 (BE-3)
- [ ] `ProofStorageService` 인터페이스 분리 (현재 `LocalProofStorageService` 단일 구현)
- [ ] `S3ProofStorageService` — 비공개 버킷, 서버 사이드 업로드, presigned GET(관리자 조회용, 짧은 만료)
- [ ] 프로필 `local`/`s3` 스위치, EC2 IAM 역할로 자격증명(키 하드코딩 금지)
- [ ] 배포 워크플로우에 버킷/리전 환경변수 추가

### E2-5. 관리자 화면 (FE, 최소)
- [ ] `/admin` 라우트 — role 체크, 미검수 목록, 증빙 미리보기, 승인/반려 버튼
- [ ] 로컬 캐시 불필요. 간단한 테이블 UI로 충분

---

## E3. GitHub 기반 경험 추론 — 담당 BE-3(클라이언트/캐시) · BE-1(분류/요약), 규모 L, 의존 E1

목표: GitHub 사용자명/URL → 직무별(BE/FE/보안/인프라/데이터·AI) 기여 비율·커밋 수·활동 개월 → `UserSpec.experiences`에 "GitHub 추정" 항목으로 채움. 사용자가 수정·삭제 가능.

### E3-0. GitHub OAuth 연동 (BE-3) — **스코프 `repo` 확정** (비공개·조직 레포 포함)
- [ ] Spring Security OAuth2 Client에 `github` registration 추가 — 로그인용이 아니라 **계정 연결용**(카카오 로그인 유지). 별도 엔드포인트 `/oauth2/authorization/github-connect` 또는 커스텀 authorize URL
- [ ] 콜백에서 access token을 **암호화 저장**(`V12 github_connections`: `user_id, github_login, access_token_enc, scopes, connected_at`). 키는 환경변수(AES-GCM), 로그 마스킹
- [ ] 연결 화면 동의 문구: 읽는 범위(비공개 레포 코드 포함), 저장하는 것(파생값만, 코드 원문 미저장), 폐기 시점, 해제 방법
- [ ] `DELETE /api/v1/users/me/github` — 토큰 폐기(GitHub `DELETE /applications/{client_id}/token`) + 파생 경험 삭제
- [ ] 토큰 만료/revoke 감지 → 상태 `DISCONNECTED`, 재연결 유도
- [ ] 합격자 제보는 OAuth 미요구 — 공개 아이디 + 동의 체크만 (E11-5)

### E3-1. GitHub 클라이언트 (BE-3)
- [ ] `GithubClient` (WebClient) — 사용자 OAuth 토큰 우선, 합격자 제보 분석은 서버 토큰(GitHub App) 사용. `Authorization`, `X-GitHub-Api-Version`
- [ ] 레이트리밋 처리: `X-RateLimit-Remaining` 헤더 읽기, 부족 시 분석 중단 + 부분 결과 저장, `Retry-After` 존중
- [ ] 호출 API: `GET /user`(연결 계정), `GET /user/repos?affiliation=owner,collaborator,organization_member&per_page=100`(비공개 포함), 합격자는 `GET /users/{u}/repos`(공개만), `GET /repos/{o}/{r}/languages`, `GET /repos/{o}/{r}/commits?author={u}&per_page=100`, `GET /repos/{o}/{r}/commits/{sha}` (files), `GET /repos/{o}/{r}/contents/{path}` (의존성 파일), GraphQL `contributionsCollection` (선택)
- [ ] 샘플링 정책: 레포당 최근 커밋 최대 N=50, 레포 최대 30개, 포크 제외, 아카이브 제외
- [ ] 입력 검증: URL → username 파싱 (`github.com/{user}`), 조직 계정 거부, 존재하지 않는 사용자 404 처리
- [ ] 단위 테스트: MockWebServer로 레이트리밋/404/페이지네이션

### E3-2. 직무 분류 규칙 (BE-1) — 정식 6종(`JobType`)으로만 분류
- [ ] `JobSignalClassifier` — 파일 경로·확장자·의존성 → `JobType` 점수 테이블 (YAML/상수)
  - `BACKEND`: `.java .kt .go .rb .php`, `.py`(Django/FastAPI/Flask 의존성 있을 때), `controller|service|repository|domain|api`, spring-boot/express/nest/django/fastapi. **인프라 신호**(`Dockerfile`, `docker-compose*`, `.github/workflows`, `terraform|k8s|helm|nginx`)는 별도 직무가 아니라 BACKEND 보조 신호(가중 0.5)로 합산
  - `FRONTEND`: `.tsx .jsx .vue .svelte .css .scss .html`, `components|pages|app|hooks|styles`, react/next/vue/svelte/tailwind
  - `SECURITY`: `security|auth|jwt|oauth|crypto|acl` 경로, spring-security/passport/bcrypt/jose/keycloak, `SecurityConfig*`, CTF/취약점 분석 레포 키워드(`ctf|exploit|pwn|vuln`)
  - `AI_ML`: `.ipynb`, torch/tensorflow/sklearn/transformers/langchain, `models|notebooks|training`
  - `DATA_ENGINEER`: airflow/dbt/spark/kafka/flink, `pipelines|etl|dags|warehouse`, `.sql` 대량
  - `PM`: 코드 신호로 식별 불가 → 분류하지 않음. README/docs 위주 기여는 `other`로 집계
  - 모호한 `.py`/`.sql`은 의존성 파일로 2차 판정, 그래도 없으면 `other`
- [ ] 노이즈 제외: lock 파일, `*.min.*`, `dist/ build/ node_modules/ vendor/`, 단일 커밋 변경 줄 수 상한(예: 5,000줄), 생성 코드
- [ ] 가중: 커밋 수 40% + 변경 파일 수 30% + 활동 개월 30% (초기값, 데모 데이터로 튜닝)
- [ ] 출력: `{JobType: ratio}` + `other` + 레포별 `{repo, primaryJob, commits, firstAt, lastAt}`
- [ ] 골든 테스트: 이 레포(HICC 팀 3명)로 BE-1/2/3 기여가 BACKEND로, `frontend/` 기여가 FRONTEND로, `security/` 기여가 SECURITY 가중을 받는지

### E3-3. 저장·비동기 (BE-2/BE-3)
- [ ] `V12__create_github_profiles.sql` — `user_id, username, analyzed_at, job_ratios JSONB, repos JSONB, commit_total, active_months, status(PENDING|DONE|FAILED|RATE_LIMITED)`
- [ ] `POST /api/v1/users/me/github` `{url}` → 202 + 상태 조회 `GET /api/v1/users/me/github`
- [ ] `@Async` 또는 Spring `TaskExecutor`로 분석 실행, 동일 사용자 동시 요청 잠금
- [ ] 재분석 쿨다운(24h), 결과 TTL
- [ ] `DELETE /api/v1/users/me/github` — 연결 해제 + 파생 experiences 제거

### E3-4. 경험 축 연결 (BE-1)
- [ ] GitHub 결과 → `UserSpec.experiences`에 `source: GITHUB` 항목 자동 생성 (레포 단위 또는 직무 단위 1개)
- [ ] 목표 직무와 일치하는 경험만 EXPERIENCE 축 카운트에 반영할지 결정 — 합격자 `experienceCount`는 직무 구분이 없으므로 **전체 카운트**로 비교하고, 직무 일치 비율은 별도 표시("목표 직무 관련 기여 62%")
- [ ] Gemini 요약(선택): README + 변경 파일 상위 20개 → "이 레포에서의 역할" 1문장. 점수에는 미반영, 표시 전용

### E3-5. 프론트 (FE)
- [ ] 프로필 탭 "GitHub 연결" 입력 + 분석 중 상태 폴링
- [ ] 직무 비율 바 차트 + 레포 목록, "GitHub에서 추정됨" 배지
- [ ] 추정 항목 편집/삭제, 수동 항목과 구분 표시

### E3-6. 리스크
- 비공개 레포 미포함 → 명시 문구
- 커밋 author 미연결 계정 → contributors API 보정
- 토큰 유출 방지: GitHub Actions secret, 로그에 토큰 마스킹

---

## E4. 데이터 부족 시 제보 유도 — 담당 FE · BE-1, 규모 S

현재: `CompareTab`에 "데이터가 부족해요" 빈 상태는 있으나 제보 화면으로의 동선 없음. 백엔드는 폴백(전체 프로필) 여부를 문구로 내려줌.

- [x] `SpecPositionResult`에 `targetJobType/targetJobLabel/jobSampleSize/minSampleSize` 명시 필드 추가
- [x] `CompareTab` NONE·OVERALL 상태에 "○○ 합격자 데이터가 N명뿐" 카드 + 제보 CTA
- [x] 홈 카드 칩 "백엔드 합격자 N명 기준"
- [x] `GET /api/v1/passers/reports/me` (V20 `reporter_user_id`) + 프로필 탭 "내 제보 N건 · 검수 대기/반영 완료"

---

## E5. 갭 ↔ 추천·로드맵 정합성 — 담당 BE-1, 규모 M

- [ ] `PromptDataBuilder`가 갭 리스트(축·percentile·미입력)를 프롬프트에 넣는 형식 고정 + 스냅샷 테스트
- [ ] Gemini 응답의 추천 활동마다 `targetGap`(어떤 축을 메우는지) 필드 요구 → `RecommendationResponse`에 노출
- [ ] 갭 축과 `Activity.tags`/`type` 매핑 규칙 기반 폴백 추천 (Gemini 실패 시 `RecommendationServiceFallbackTest` 확장)
- [ ] 로드맵 스텝이 갭 우선순위 순서를 따르는지 검증 테스트
- [ ] 추천 카드에 "이 활동은 [자격증] 갭을 줄여요" 표시 (FE)

---

## E6. 활동 데이터 품질·검색 — 담당 BE-2, 규모 M

- [ ] `GET /activities` 필터 확장: `jobType`(tags 기반), `deadlineAfter`, 키워드 검색(`name/organization ILIKE`)
- [ ] 마감 지난 활동 자동 비활성화 스케줄러 (`deadline < today` → `is_active=false`)
- [ ] 크롤러 재수집 파이프라인 문서화 + 증분 시드 생성 (기존 데이터 중복 방지 키 `url`)
- [ ] `targetSpec` JSON 스키마 고정 및 검증 테스트

---

## E7. 인프라·운영 — 담당 BE-3, 규모 M

- [x] 프론트 CI: `.github/workflows/frontend-ci.yml` — Node 22, tsc/lint/build, `frontend/**` 경로 트리거
- [ ] 탄력적 IP 고정, 카카오 리다이렉트 URI 운영값 반영 (메모리상 미완)
- [ ] `backend-deploy.yml` 헬스체크 후 롤백 단계
- [ ] 구조화 로깅 + Gemini 호출 실패율/지연 메트릭 (Actuator + 간단 대시보드 또는 CloudWatch)
- [ ] Gemini 일일 호출 상한 전역 가드 (현재 사용자별 `dailyUpdateCount`만 존재)
- [ ] `OldVsNewScoreComparisonDemo`를 `@Tag("demo")`로 분리해 CI에서 제외
- [ ] 증빙 파일 로컬 경로 백업/정리 크론 (E2-4 전까지)

---

## E8. 인증·계정 — 담당 BE-3, 규모 S~M

- [ ] 회원 탈퇴 `DELETE /api/v1/users/me` — spec/target/recommendation/roadmap/refresh token/github profile cascade, 제보 데이터는 익명 유지
- [ ] 구글 로그인 (FE에 "곧 지원 예정" 문구 존재, `User.provider`에 GOOGLE 예약됨)
- [ ] 리프레시 토큰 회전 시 재사용 감지(탈취 대응) 테스트
- [ ] 닉네임 변경 API

---

## E11. 경험 심화 — 기여 프로필·외부 소스·검증 — 담당 BE-1/BE-3/FE, 규모 L, 의존 E1·E3

설계 원칙
- **두 층 분리**: 층 1 = 경험 개수(합격자와 같은 단위로 percentile, E1). 층 2 = 기여 프로필(`areas`/`stack`/깊이) — 점수화하지 않고 **커버리지·설명·추천**에만 사용.
- **같은 프로젝트라도 사람마다 다르게**: 레포 전체가 아니라 author 필터된 커밋·파일 기준으로 영역을 뽑는다.
- **원본 미저장**: 코드·diff·자소서 원문은 저장하지 않는다. 자소서는 받지 않는다(민감·검증 불가·GitHub 파생값과 중복).
- **검증 등급은 표시만**: `GITHUB_VERIFIED / LINK_PROVIDED / SELF_REPORT`는 가중치가 아니라 배지.

### E11-1. 기여 프로필 추출기 (BE-1) — 규칙 기반, 결정적
- [ ] `ContributionProfiler` — author 필터 커밋의 파일 경로·확장자·의존성 → `areas` 태그. E3-2 `JobSignalClassifier`와 같은 규칙 테이블에서 파생(영역 레벨이 더 세분화: AUTH, API, DB, CI_CD, TEST, UI, STATE_MGMT, DATA_PIPELINE, ML_MODEL, INFRA, DOCS)
- [ ] 레포별 `contributionRatio` = 본인 커밋 / 전체 커밋 (contributors API), 본인이 **생성한** 파일 수
- [ ] 테스트·CI 신호: `src/test|__tests__|*.spec.*|*.test.*` 및 `.github/workflows` 수정 여부 → `TEST`, `CI_CD` 영역
- [ ] PR·리뷰: 머지된 PR 수, 남긴/받은 리뷰 수 (`GET /search/issues?q=author:{u} is:pr is:merged`, `reviewed-by:`) → `evidence.collaboration`
- [ ] `topPaths` 상위 5개 경로, `stack` = 의존성 파일에서 추출한 라이브러리 상위 10개
- [ ] 합격자 분석 시 `year` 이전 커밋만 집계(합격 당시 상태 복원)
- [ ] 골든 테스트: 이 레포로 BE-3 → `AUTH, API, CI_CD`, BE-1 → `API`(추천), FE 기여 → `UI`

### E11-2. 직무별 요구 영역 커버리지 (BE-1)
- [ ] 1차: 팀 정의 체크리스트 `JobAreaRequirements` (6직무 × 5~7영역, YAML). 예) BACKEND: API, DB, AUTH, TEST, CI_CD, INFRA
- [ ] 2차(E11-5 이후): 합격자 `areas` 분포로 교체 — "BACKEND 합격자 80%가 AUTH 보유". 표본 `coverage` 별도 표시, `MIN_SAMPLE` 동일 적용
- [ ] `SpecPositionResult`에 `areaCoverage: [{area, label, hasEvidence, verification, passerRatio?}]` 추가 — **percentile 축이 아님**, 별도 섹션
- [ ] 갭 정렬: 없는 영역 중 합격자 보유율 높은 순 (2차) / 체크리스트 순 (1차)
- [ ] `PromptDataBuilder`: 보유/미보유 영역 + 각 경험의 `role`·`stack`을 프롬프트에 포함 → 추천이 "배포 경험을 쌓을 활동"처럼 좁혀지도록. 스냅샷 테스트
- [ ] 로드맵: 미보유 영역을 채우는 순서로 스텝 구성 검증

### E11-3. Gemini 깊이 판정 (BE-1) — 선택, 캐시·상한 뒤에
- [ ] 레포당 본인 diff 샘플 최대 20개(파일당 200줄 컷) → Gemini에 "이 기여가 어떤 영역이고, 직접 구현/설정/반복 CRUD 중 어디에 해당하는지" JSON 요청. 코드 원문은 요청 후 폐기, 응답의 `areas`·`depth(IMPLEMENTED|CONFIGURED|BOILERPLATE)`·`roleSummary`만 저장
- [ ] 규칙 추출 결과와 충돌 시 규칙 우선, Gemini는 `roleSummary`와 depth만 보강
- [ ] 레포별 결과 캐시(커밋 SHA 기준 무효화), 사용자당 일일 Gemini 호출 상한(E7 전역 가드와 연동)
- [ ] 사용자가 `roleSummary` 수정 가능, 수정 시 `verification` 유지하되 `edited=true`

### E11-4. 외부 소스 (BE-3 연동 · BE-1 매핑)
- [ ] **solved.ac** — `GET https://solved.ac/api/v3/user/show?handle=` (공개, 토큰 불필요). 티어·푼 문제 수 → `experiences`가 아닌 `UserSpec.codingTest { tier, solved }` 별도 필드. 합격자 제보에도 선택 입력 → 코딩테스트 축 후보(층 1 방식 percentile 가능, **단위 동일**)
- [ ] **활동 DB 자동 매칭** — 경험 `title`을 `Activity.name`과 퍼지 매칭(정규화 후 trigram) → 일치 시 `Activity.tags`/`targetSpec`에서 `areas` 자동 부여, `source: ACTIVITY_MATCH`. 비용 0
- [ ] **기술 블로그 RSS** (velog/tistory) — 글 수·제목 키워드 → `areas` 보조 신호. 본문 미저장
- [ ] **Kaggle / Hugging Face** (AI_ML) — 공개 프로필 API, 대회·모델 수
- [ ] **Figma/Notion 공개 링크** (PM) — 링크 존재 + Gemini 구조화(기획 영역 태그). PM은 코드 신호가 없으므로 층 2의 유일한 소스. 원문 미저장
- [ ] 모든 외부 소스: 사용자당 재조회 쿨다운 24h, 실패 시 마지막 성공값 유지

### E11-5. 합격자 측 동의 기반 수집 (BE-2/BE-3)
- [ ] 제보 폼: GitHub 아이디 **선택 입력** + 동의 체크(분석 범위: 공개 레포만 / 저장: 파생값만 / 아이디·레포명·URL 미저장 / 분석 직후 폐기)
- [ ] 서버: 제보 수신 시 즉시 `ContributionProfiler` 실행(서버 토큰, 공개 레포, `year` 이전 커밋) → `PasserData`에 `areas TEXT[]`, `stack TEXT[]`, `github_derived JSONB {repos, commits, activeMonths, jobRatios}` 저장. 아이디는 요청 처리 후 어디에도 남기지 않음(로그 마스킹 테스트)
- [ ] `V14__passer_data_contribution.sql`
- [ ] `JobSpecProfileBuilder`에 영역 보유율 집계 추가(`areaRatios`, `githubSampleSize`)
- [ ] 자소서·포트폴리오 파일은 받지 않음 (결정). 증빙 이미지(합격 통보)만 유지
- [ ] solved.ac 핸들도 동일 방식(선택·즉시 조회·핸들 미저장)

### E11-6. Gemini 심층 질문 (BE-1/FE) — UX, E11-3 이후
- [ ] 경험 입력 직후 Gemini가 내용 기반 후속 질문 2~3개 생성("토큰 만료는 어떻게 처리했나요?") → 답변을 `areas` 확정·`roleSummary` 보강에 사용. **점수 없음**
- [ ] 답변 원문은 저장하지 않고 구조화 결과만. 건너뛰기 가능
- [ ] 일일 호출 상한 포함

### E11-7. 프론트 (FE)
- [ ] 경험 카드: 유형·기간·역할 한 줄·`areas` 칩·검증 배지·출처(GitHub/수동/활동매칭)
- [ ] GitHub 연결 버튼(OAuth) + 동의 모달 + 분석 진행 상태
- [ ] 비교 탭 "영역 커버리지" 섹션 (체크리스트 형태, percentile 막대와 시각적으로 구분)
- [ ] 추천 카드에 "이 활동으로 채워지는 영역" 표시
- [ ] 제보 폼 GitHub/solved.ac 선택 입력 + 동의 체크

### E11-8. 리스크·정책
- [ ] `repo` 스코프 동의율이 낮을 수 있음 → 연결 화면에 "비공개 레포 없이 연결" 옵션(`read:user`만)도 제공, 결과에 "공개 레포 기준" 표시
- [ ] 비공개 레포 코드가 Gemini로 전송됨(E11-3) → 동의 문구에 명시, 끄기 옵션
- [ ] 토큰 유출 = 사용자 비공개 코드 노출 → 암호화 키 분리, 토큰 DB 접근 감사 로그, 연결 해제 시 GitHub 측 revoke까지 확인
- [ ] 인턴 회사 레포는 대부분 조직 레포이며 조직이 OAuth App 접근을 막았을 수 있음 → "조직 승인 필요" 안내
- [ ] 표본 감소: 합격자 GitHub 제공자 수를 별도 `coverage`로 표시, 없으면 1차 체크리스트로 폴백

---

## E10. 기능 명세서 보류 항목 (노션 F-08 · F-09, Could) — 규모 M

### E10-1. F-08 마감 알림 (BE-3)
- [ ] 관심 활동 저장 `POST/DELETE /api/v1/users/me/bookmarks/{activityId}` + `bookmarks` 테이블
- [ ] 알림 채널: 이메일 불가(카카오 이메일 미수집 결정), 카카오 알림톡은 비즈앱 전환 필요(포기한 바 있음) → **인앱 알림**으로 한정
- [ ] 스케줄러: 매일 00:05 D-7/D-1 대상 조회 → `notifications` 적재 (v8 "D-day 자정 박제" 규칙과 동일 기준으로 `deadline` 비교)
- [ ] `GET /api/v1/users/me/notifications` + 읽음 처리, 홈 탭 배지(FE)

### E10-2. F-09 추천 피드백 (BE-1)
- [ ] `POST /api/v1/recommendations/{activityId}/feedback` `{reaction: LIKE|DISLIKE}` + 테이블
- [ ] `PromptDataBuilder`에 "사용자가 싫어한 활동 유형" 반영
- [ ] 7/14 결정(유저당 1건 + 24시간 캐시)과 충돌 — 피드백 시 1회 재생성 허용(`dailyUpdateCount` 소모)할지, 다음 갱신 때 반영한다고 안내만 할지 결정
- [ ] 관리자 집계: 활동별 LIKE/DISLIKE 비율 → 시드 품질 점검(E2-5 관리자 화면에 탭 추가)

---

## E9. 품질·테스트 — 전원, 규모 S

- [x] `SpecPositionServiceCacheTest` — Caffeine 실제 올린 슬라이스 테스트 5건 (캐시 적중·폴백 지연 조회·null 키·evictAll)
- [ ] 추천·로드맵 E2E 시나리오 테스트: 스펙 저장 → 추천 → 로드맵이 같은 갭을 참조
- [ ] 프론트 컴포넌트 테스트 최소 세트 (CompareTab 막대/미입력 렌더링)
- [ ] PROJECT.md "주요 요청 흐름"에 v9 위치·갭 흐름 반영

---

## 제안 실행 순서 (스프린트 단위)

1. **S1**: E0 (직무 enum) + E1-1~E1-4 (경험 축) + E7 프론트 CI + E9 캐시 테스트
2. **S2**: E2-1~E2-3 (검수 API) + E4 (제보 유도) → 데이터 루프 완성
3. **S3**: E3-0 (GitHub OAuth `repo`) + E3-1~E3-3 (클라이언트·분류·저장) + E11-1 (기여 프로필 추출기)
4. **S4**: E3-4~E3-5 (경험 축 연결·UI) + E11-2 (커버리지 1차 체크리스트) + E11-4 solved.ac·활동 DB 매칭 + E5 (갭-추천 정합성)
5. **S5**: E11-5 (합격자 동의 기반 수집 → 커버리지 2차) + E11-7 (FE) + E2-4 (S3 전환)
6. **S6**: E11-3 (Gemini 깊이 판정) + E11-6 (심층 질문) + E11-4 나머지 소스
7. **S7**: E6 + E8 + E10

확정된 결정
- GitHub OAuth 스코프: **`repo`** (비공개·조직 레포 포함). `read:user`만의 "공개 레포 연결" 옵션은 병행 제공
- 경험 저장 구조: **리스트(JSONB)**, 정수 카운트 옵션 폐기
- EXPERIENCE 축 가중치: **없음** — 개수만 percentile, 질적 정보는 커버리지·추천에만
- 자소서·포트폴리오 파일: **받지 않음**
- 합격자 GitHub: 공개 아이디 선택 입력 + 동의, 즉시 추출 후 아이디 폐기, 파생값만 저장

각 스프린트 시작 전 팀 결정 사항:
- E1: 7/14 "경험 제외" 결정 번복 → 노션 회의록·기능 명세서 F-01/F-04 갱신
- E0/E3: 인프라/DevOps를 7번째 직무로 추가할지 (추가하지 않으면 BACKEND 보조 신호로 처리)
- E1-1: "경험 1개로 세는 기준" 문구 확정
- E11-2: 직무별 요구 영역 1차 체크리스트 (6직무 × 5~7영역) 내용
- E11-3: 비공개 레포 diff를 Gemini로 보내는 것 허용 여부(동의 문구·끄기 옵션 전제)
- E2-1: 관리자 부여 방식
- E3-4: GitHub 경험 카운트 기준(전체 vs 직무 일치)
- E10-2: 피드백 즉시 반영 vs 24h 캐시 유지
