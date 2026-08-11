# Spec Road

사용자의 목표 직무와 현재 스펙을 바탕으로 활동을 추천하고 시기별 커리어 로드맵을 제공하는 AI 기반 진로 추천 서비스입니다.

- Backend: [`backend`](./backend) — Spring Boot REST API
- Frontend: [`frontend`](./frontend) — Next.js
- 상세 설계: [`PROJECT.md`](./PROJECT.md)

## 주요 기능

- 사용자 스펙 및 목표 직무 저장·조회
- 인턴십·교육·공모전·대외활동 목록 및 상세 조회
- 실제 DB 활동을 활용한 AI 맞춤 추천
- 학년 기준 시기별 커리어 로드맵 생성
- 익명 합격자 데이터 기반 스펙 비교 및 Match Score 계산
- 합격자 스펙 제보 및 검수용 증빙 이미지 업로드
- 링커리어 활동 수집, 검수용 HTML 및 SQL 시드 생성

## 기술 스택

### Backend

- Java 17 / Spring Boot 3.2.5
- Spring Data JPA / Hibernate
- PostgreSQL 16 / Flyway
- Spring Security / JWT / OAuth2 (카카오)
- Google Gemini API (`gemini-2.5-flash`)
- Caffeine Cache
- SpringDoc OpenAPI (Swagger UI)

### Frontend

- Next.js 16.2.10
- React 19.2.4
- TypeScript 5
- Tailwind CSS 4

### Data Collection

- Python 3.9+
- 링커리어 활동 크롤러
- JSON·CSV·SQL·HTML 검수 파일 생성

### Infrastructure

- Docker / Docker Compose
- AWS EC2 / RDS
- Vercel
- GitHub Actions

## 팀 역할

| 역할 | 담당자 | GitHub | 담당 영역 |
| --- | --- | --- | --- |
| BE-1 · AI/추천 | 최서영 | [@choiseoyoungo](https://github.com/choiseoyoungo) | Gemini 연동, 추천·로드맵 생성, Match Score, DB 활동 매칭 |
| BE-2 · DB/코어·데이터 | 조재성 | [@wbfkr0980-tech](https://github.com/wbfkr0980-tech) | Entity·Repository·Flyway, UserSpec·Activity·PasserData, 활동 시드·크롤러, 일부 FE 연동 |
| BE-3 · API/인프라 | 이지우 | [@011201Leejiwoo](https://github.com/011201Leejiwoo) | Controller, Security, JWT·카카오 OAuth2, Swagger, 배포, FE-BE 연동 |

## 데이터 안내

- 활동 데이터는 크롤러 출력 결과를 사람이 검수한 뒤 SQL 시드로 반영합니다.
- 합격자 데모 데이터는 발표 및 기능 검증을 위한 가상 데이터입니다.
- 사용자가 제보한 합격자 데이터는 검수 전 추천과 비교 계산에서 제외됩니다.
- 증빙 이미지는 현재 로컬 파일 시스템에 임시 저장하며, 실제 운영 전 비공개 객체 저장소로 전환할 예정입니다.

