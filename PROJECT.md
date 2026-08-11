# Spec Road 프로젝트 설계 문서

## 1. 프로젝트 개요

Spec Road는 사용자의 목표 직무와 현재 스펙을 기반으로 인턴십·교육·공모전·대외활동을 추천하고, 학년과 시기에 맞는 커리어 로드맵을 제공하는 서비스입니다.

추천과 로드맵 생성 시 Gemini에 현재 사용자 스펙, 목표 직무, 합격자 비교 데이터와 DB에 저장된 신청 가능한 활동 목록을 전달합니다. Gemini가 선택한 활동 ID는 다시 DB와 대조하여 실제 존재하는 활동만 응답에 포함합니다.

이 프로젝트의 활동 매칭은 DB 데이터를 프롬프트에 주입하는 RAG 형태의 패턴이며, 현재 `pgvector`나 벡터 유사도 검색은 사용하지 않습니다.

## 2. 팀 역할

| 역할 | 담당자 | GitHub | 담당 영역 |
| --- | --- | --- | --- |
| BE-1 · AI/추천 | 최서영 | [@choiseoyoungo](https://github.com/choiseoyoungo) | Gemini API, 추천·로드맵 생성, Match Score, 합격자 유사 스펙 탐색 |
| BE-2 · DB/코어·데이터 | 조재성 | [@wbfkr0980-tech](https://github.com/wbfkr0980-tech) | DB 설계, Entity·Repository·Flyway, UserSpec·Activity·PasserData, 시드·크롤러, 일부 FE 연동 |
| BE-3 · API/인프라 | 이지우 | [@011201Leejiwoo](https://github.com/011201Leejiwoo) | REST Controller, Spring Security, JWT·카카오 OAuth2, Swagger, AWS·CI/CD, FE-BE 연동 |

### BE-1 주요 파일

- `GeminiService` — Google Gemini API 호출과 프롬프트 관리
- `RecommendationService` — DB 활동 기반 맞춤 추천 생성 및 캐시 처리
- `RoadmapService` — 시기별 로드맵 생성 및 실제 DB 활동 매칭
- `MatchScoreCalculator` — 사용자와 합격자 스펙 비교 점수 계산
- `SimilarSpecFinder` — 목표 직무와 정규화 학점을 기준으로 유사 합격자 탐색
- `GlobalCertPoolService` — 자격증 비교용 데이터 조회 및 Caffeine 캐시 관리

### BE-2 주요 파일 및 데이터

- Entity: `User`, `UserSpec`, `TargetJob`, `Activity`, `PasserData`, `Recommendation`, `RoadmapCache`
- Repository: 전체 Spring Data JPA Repository
- Service: `UserService`, `UserSpecService`, `TargetJobService`, `ActivityService`, `PasserReportService`, `LocalProofStorageService`
- DB 마이그레이션: `backend/src/main/resources/db/migration/`
- 검수된 시드: `backend/seed/`
- 활동 크롤러: `backend/crawler/linkareer_crawler.py`
- 크롤러는 DB에 직접 쓰지 않고 JSON·CSV·SQL·HTML 파일을 생성하며, 사람이 검수한 SQL만 DB에 반영

### BE-3 주요 파일

- Controller: `AuthController`, `UserController`, `ActivityController`, `RecommendationController`, `PasserReportController`
- Security: `JwtTokenProvider`, `JwtAuthenticationFilter`, OAuth2 로그인 성공·실패 처리
- `GlobalExceptionHandler`
- `SwaggerConfig`와 SpringDoc OpenAPI 문서
- AWS EC2·RDS 배포 및 GitHub Actions CI/CD

## 3. 시스템 구조

```text
Next.js 16 / React 19 / TypeScript
                │
                │ REST API / JWT
                ▼
Spring Boot 3.2.5 / Java 17
    ├── Controller
    ├── Service
    ├── Repository
    ├── PostgreSQL 16
    ├── Google Gemini API
    └── Kakao OAuth2

활동 수집 경로
링커리어 → Python 크롤러 → HTML 검수 → SQL 시드 → PostgreSQL

배포
Frontend: Vercel
Backend: AWS EC2 + Docker
Database: AWS RDS for PostgreSQL
```

## 4. 주요 요청 흐름

### UserSpec 저장

```text
Frontend
  → PUT /api/v1/users/me/spec
  → UserController
  → UserSpecService
  → UserSpecRepository
  → PostgreSQL
```

지원하는 어학 성적은 `TOEIC`, `TOEFL`, `OPIC`이며, 자격증은 여러 개 저장할 수 있습니다.

### 활동 조회

```text
Frontend
  → GET /api/v1/activities
  → ActivityController
  → ActivityService
  → ActivityRepository
  → 마감되지 않은 활성 활동 반환
```

활동 유형은 다음 네 가지 문자열을 사용합니다.

- `INTERNSHIP`
- `EDUCATION`
- `COMPETITION`
- `EXTERNAL`

### 맞춤 추천

```text
사용자 스펙·목표 직무 조회
  → 유사 합격자 탐색 및 Match Score 계산
  → 신청 가능한 DB 활동 조회
  → 사용자·합격자·활동 데이터를 Gemini 프롬프트에 포함
  → Gemini가 반환한 활동 UUID를 DB와 대조
  → 실제 활동만 추천 응답으로 반환
  → 성공 결과 캐시 저장
```

Gemini 호출이나 파싱이 실패하면 DB 활동 기반 기본 추천을 반환합니다.

### 커리어 로드맵

```text
사용자 스펙·목표 직무·학년 조회
  → 유사 합격자와 추천 활동 데이터 구성
  → Gemini가 시기별 타임라인 생성
  → 반환된 활동 UUID를 DB와 대조
  → 유효한 활동을 matchedActivities에 포함
  → 성공 결과 캐시 저장
```

### 합격자 제보

```text
Frontend
  → POST /api/v1/passers/reports (multipart/form-data)
  → 합격자 스펙 JSON + 증빙 이미지 제출
  → 이미지 형식·크기 검증
  → 파일 시스템에 이미지 저장
  → passer_data에 USER_REPORT / is_verified=false 저장
```

검수 전 제보는 추천과 합격자 비교 계산에서 제외됩니다. 현재 증빙 이미지는 로컬 파일 시스템에 임시 저장하며, 실제 운영 전 비공개 객체 저장소 적용이 필요합니다.

## 5. 백엔드 패키지 구조

```text
com.career.recommendation
├── config/
│   ├── CacheConfig.java
│   ├── SecurityConfig.java
│   └── SwaggerConfig.java
├── controller/
│   ├── AuthController.java
│   ├── UserController.java
│   ├── ActivityController.java
│   ├── RecommendationController.java
│   └── PasserReportController.java
├── service/
│   ├── UserService.java
│   ├── UserSpecService.java
│   ├── TargetJobService.java
│   ├── ActivityService.java
│   ├── PasserReportService.java
│   ├── LocalProofStorageService.java
│   ├── GeminiService.java
│   ├── RecommendationService.java
│   ├── RoadmapService.java
│   ├── RecommendationCacheService.java
│   ├── RoadmapCacheService.java
│   └── GlobalCertPoolService.java
├── repository/
├── entity/
├── dto/
├── security/
├── exception/
└── util/
    ├── MatchScoreCalculator.java
    ├── SimilarSpecFinder.java
    └── PromptDataBuilder.java
```

추천과 로드맵 API는 `RecommendationController`에서 제공합니다. 별도의 `RoadmapController`는 없습니다.

## 6. 기술 스택

| 구분 | 기술 | 버전·설명 |
| --- | --- | --- |
| Backend | Java | 17 |
| Backend | Spring Boot | 3.2.5 |
| Persistence | Spring Data JPA / Hibernate | ORM 및 영속성 관리 |
| Database | PostgreSQL | 16, JSONB·배열 타입 활용 |
| Migration | Flyway | DB 스키마 버전 관리 |
| Security | Spring Security / JJWT | JWT 인증과 인가 |
| Social Login | OAuth2 Client | 카카오 로그인 구현, 구글 로그인 미구현 |
| AI | Google Gemini API | `gemini-2.5-flash` |
| Cache | Caffeine | 자격증 비교 데이터 인메모리 캐시 |
| API Docs | SpringDoc OpenAPI | 2.5.0, Swagger UI 제공 |
| Frontend | Next.js | 16.2.10 |
| Frontend | React | 19.2.4 |
| Frontend | TypeScript | 5 |
| Styling | Tailwind CSS | 4 |
| Crawler | Python | 3.9 이상, 외부 패키지 없이 실행 |
| Container | Docker / Docker Compose | 로컬 DB 및 백엔드 배포 |
| Deployment | AWS EC2 / RDS, Vercel | BE·DB·FE 배포 |
| CI/CD | GitHub Actions | 테스트 및 배포 자동화 |

## 7. 개발 환경

| 환경 | Backend | Frontend | Database |
| --- | --- | --- | --- |
| 로컬 | `localhost:8080` | `localhost:3000` | PostgreSQL `localhost:5433` |
| 배포 | AWS EC2 | Vercel | AWS RDS PostgreSQL |

로컬 PostgreSQL 컨테이너 내부 포트는 `5432`이며, 호스트에서는 `5433`으로 연결합니다.

## 8. 주요 환경변수

환경변수는 Git에 올리지 않고 로컬 설정 또는 배포 환경에서 관리합니다.

```dotenv
GEMINI_API_KEY=...
KAKAO_CLIENT_ID=...
KAKAO_CLIENT_SECRET=...
JWT_SECRET=...
DB_URL=jdbc:postgresql://...
DB_USERNAME=...
DB_PASSWORD=...
FRONTEND_REDIRECT_URI=...
CORS_ALLOWED_ORIGINS=...
PASSER_PROOF_STORAGE_PATH=...
```

## 9. 현재 운영 범위와 주의사항

- 합격자 데모 데이터는 발표와 기능 검증을 위해 만든 가상 데이터입니다.
- 사용자 제보 데이터는 검수 전 추천과 비교에 사용하지 않습니다.
- 합격자 제보의 관리자 승인·반려 화면과 전용 검수 API는 아직 구현되지 않았습니다.
- 증빙 이미지는 현재 로컬 파일 시스템에 저장되며 컨테이너 재배포 시 보존이 보장되지 않습니다.
- Gemini 실패 시 서비스 중단 대신 DB 활동 기반 기본 추천과 기본 로드맵을 반환합니다.
- 실제 운영 전 HTTPS, 운영용 JWT 비밀키, 비공개 증빙 저장소와 관리자 검수 기능을 적용해야 합니다.
