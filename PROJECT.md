# Spec Road — 프로젝트 설계 문서

## 👥 팀원 배정 (확정)

| 역할 | 담당자 | GitHub |
| --- | --- | --- |
| BE-1 · AI/추천 | 최서영 | https://github.com/choiseoyoungo |
| BE-2 · DB·코어+FE | 조재성 | https://github.com/wbfkr0980-tech |
| BE-3 · API·인프라+FE | 이지우 | https://github.com/011201Leejiwoo |

---

## 👥 BE 3인 역할 분배

| 역할 | 담당 영역 | 핵심 기술 |
| --- | --- | --- |
| **BE-1 (AI/추천)** · 최서영 | Gemini API 연동, 추천 엔진, matchScore, RAG 파이프라인 | Gemini API, 프롬프트 엔지니어링, pgvector |
| **BE-2 (DB/코어)** · 조재성 | DB 설계, JPA Entity, 사용자·스펙·활동·합격자 CRUD | Spring Data JPA, PostgreSQL, Flyway |
| **BE-3 (API/인프라)** · 이지우 | REST API 개발, JWT·OAuth2 인증, Swagger, 배포 | Spring Security, SpringDoc, AWS, GitHub Actions |

### BE-1 세부 담당

- `RecommendationService` — Gemini API 호출 및 파싱
- `GeminiService` — 프롬프트 템플릿 관리 및 Google Gemini API 연동
- `MatchScoreCalculator` — 스펙 매칭 점수 계산
- `RoadmapService` — 로드맵 생성 로직 + **RAG 패턴 기반 DB 활동 매칭**
  - `enrichWithMatchedActivities()` — AI 생성 타임라인에 시기별 실제 DB 활동을 자동 매칭 (할루시네이션 방지)
  - `parseMonthRange()` — AI가 생성한 period 텍스트(예: `"3학년 2학기 (9~11월)"`)에서 월 범위 자동 파싱
  - `findActivitiesForPeriod()` — 파싱된 월 범위로 DB 대외활동 조회 (시기당 최대 3개, 마감 지난 활동 제외)
- `SimilarSpecFinder` — 합격자 유사 케이스 검색

### BE-2 세부 담당

- Entity: `User`, `UserSpec`, `Activity`, `PasserData`, `TargetJob`
- Repository: 전체 JPA Repository
  - `ActivityRepository` — **기간별 활성 활동 조회 쿼리 포함** (`findByIsActiveTrueAndDeadlineBetweenOrderByDeadlineAsc`)
- Service: `UserService`, `ActivityService`, `PasserService`
- DB 마이그레이션: Flyway 스크립트 관리
- 합격자 데이터 익명화 처리
- **Activity 테이블 데이터 정비** — 로드맵 매칭에 필요한 `url`(지원 링크), `deadline`(마감일) 필드 빠짐없이 등록

### BE-3 세부 담당

- Controller: 전체 REST API 엔드포인트
- `JwtTokenProvider`, `JwtAuthenticationFilter`
- OAuth2 소셜 로그인 (카카오·구글)
- `GlobalExceptionHandler`
- Swagger/SpringDoc API 문서
  - **로드맵 API 응답에 `matchedActivities` 필드 스키마 반영**
- AWS EC2 + RDS 배포, GitHub Actions CI/CD

---

## 🏗️ 시스템 아키텍처

```
┌─────────────────────────────────────────────────────┐
│                   Client (React · TS)                │
└──────────────────────┬──────────────────────────────┘
                       │ HTTPS / JWT
┌──────────────────────▼──────────────────────────────┐
│              Spring Boot 서버 (Java 17)              │
│  ┌──────────────┐ ┌──────────────┐ ┌─────────────┐ │
│  │  Controller  │ │   Service    │ │  Repository │ │
│  │  (BE-3)      │ │  (BE-1/2/3) │ │  (BE-2)     │ │
│  └──────┬───────┘ └──────┬───────┘ └──────┬──────┘ │
│         └────────────────┼────────────────┘         │
│                          │                           │
│  ┌───────────────────────▼───────────────────────┐  │
│  │            External Services                  │  │
│  │  ┌──────────────┐  ┌────────────────────┐    │  │
│  │  │ Gemini API   │  │  PostgreSQL 15      │    │  │
│  │  │ (추천 생성)   │  │  (데이터 저장)     │    │  │
│  │  └──────┬───────┘  └─────────┬──────────┘    │  │
│  │         │                    │                │  │
│  │         ▼                    ▼                │  │
│  │  ┌────────────────────────────────────────┐  │  │
│  │  │       RAG 파이프라인 (RoadmapService)   │  │  │
│  │  │  AI 로드맵 생성 → DB 실제 활동 매칭     │  │  │
│  │  │  (할루시네이션 방지)                     │  │  │
│  │  └────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘

배포: AWS EC2 (BE) · AWS RDS (DB) · Vercel (FE)
```

### RAG 파이프라인 흐름 (로드맵)

```
사용자 요청 → getRoadmap()
  ├─ [1단계] AI(Gemini)가 6개월 커리어 타임라인 생성
  │          (period, priority, activity, reason)
  └─ [2단계] enrichWithMatchedActivities()
       ├─ period 텍스트에서 월 범위 파싱 ("9~11월" → [9, 11])
       ├─ ActivityRepository에서 해당 기간 내 마감 & 활성 활동 조회
       ├─ 이미 지난 마감일 제외, 시기당 최대 3개 매칭
       └─ matchedActivities에 실제 활동(이름·마감일·URL) 주입 → 응답 반환
```

---

## 📦 패키지 구조

```
com.career.recommendation
├── config/
│   ├── SecurityConfig.java        (BE-3)
│   └── SwaggerConfig.java         (BE-3)
├── controller/
│   ├── AuthController.java        (BE-3)
│   ├── UserController.java        (BE-3)
│   ├── ActivityController.java    (BE-3)
│   ├── RecommendationController.java (BE-3)
│   ├── RoadmapController.java     (BE-3)
│   └── PasserController.java      (BE-3)
├── service/
│   ├── AuthService.java           (BE-3)
│   ├── UserService.java           (BE-2)
│   ├── ActivityService.java       (BE-2)
│   ├── PasserService.java         (BE-2)
│   ├── RecommendationService.java (BE-1)
│   ├── RoadmapService.java        (BE-1) ← RAG 매칭 로직 포함
│   └── GeminiService.java         (BE-1)
├── repository/
│   ├── UserRepository.java        (BE-2)
│   ├── UserSpecRepository.java    (BE-2)
│   ├── ActivityRepository.java    (BE-2) ← 기간별 활동 조회 쿼리 포함
│   └── PasserDataRepository.java  (BE-2)
├── entity/
│   ├── User.java                  (BE-2)
│   ├── UserSpec.java              (BE-2)
│   ├── Activity.java              (BE-2)
│   └── PasserData.java            (BE-2)
├── dto/
│   ├── roadmap/
│   │   └── RoadmapResponse.java   (BE-1) ← MatchedActivity DTO 포함
│   ├── recommendation/
│   │   └── RecommendationResponse.java (BE-1)
│   ├── request/                   (BE-3)
│   └── response/                  (BE-3)
├── util/
│   ├── MatchScoreCalculator.java  (BE-1)
│   ├── SimilarSpecFinder.java     (BE-1)
│   └── RecommendationFallbackData.java (BE-1)
├── security/
│   ├── JwtTokenProvider.java      (BE-3)
│   └── JwtAuthenticationFilter.java (BE-3)
└── exception/
    └── GlobalExceptionHandler.java (BE-3)
```

---

## 🛠️ 기술 스택 & 선정 이유

| 기술 | 버전 | 선정 이유 |
| --- | --- | --- |
| Spring Boot | 3.2 | Java 친숙도, IntelliJ 지원, 엔터프라이즈 표준 |
| Java | 17 | LTS 버전, Record/sealed class 지원 |
| PostgreSQL | 15 | 복잡한 관계형 데이터, JSONB 지원 |
| Spring Data JPA | - | ORM 표준, Flyway와 연동 |
| Spring Security | - | JWT + OAuth2 통합 지원 |
| Gemini API | gemini-2.5-flash | 고품질 추천, 빠른 응답 속도 및 JSON Mode 파싱 안정성 |
| Flyway | - | DB 마이그레이션 버전 관리 |
| SpringDoc (Swagger) | - | API 문서 자동화 |
| GitHub Actions | - | CI/CD 자동화 |
| Next.js | 16.2.10 (React 19.2.4 기반) | 라우팅 내장, API 연동 편의 |

---

## 🌐 개발 환경

| 환경 | BE | FE | DB |
| --- | --- | --- | --- |
| 로컬 | localhost:8080 | localhost:3000 | PostgreSQL local (port 5432) |
| 개발서버 | EC2 dev | Vercel preview | RDS dev |
| 프로덕션 | EC2 prod | Vercel prod | RDS prod |

---

## 🔑 환경변수 목록

```yaml
# application-secret.yml (git 제외)
GEMINI_API_KEY: AIzaSy...
KAKAO_CLIENT_ID: ...
GOOGLE_CLIENT_ID: ...
JWT_SECRET: ...
DB_URL: jdbc:postgresql://...
DB_USERNAME: ...
DB_PASSWORD: ...
```
