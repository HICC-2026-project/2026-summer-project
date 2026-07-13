# Spec Road

AI 기반 커리어 추천 서비스 **Spec Road**입니다.
- Backend: [`backend`](./backend) — Spring Boot
- Frontend: [`frontend`](./frontend) — Next.js

## 기술 스택
- Java 17 / Spring Boot 3.2
- PostgreSQL 16 / Spring Data JPA / Flyway
- Spring Security + JWT + OAuth2 (카카오·구글)
- Claude API (Anthropic) — 활동 추천 및 로드맵 생성
- SpringDoc (Swagger UI)
- Next.js 

## 팀 역할
| 역할 | 담당자 | GitHub | 업무 |
|---|---|---|---|
| BE-1 | 최서영 | [@choiseoyoungo](https://github.com/choiseoyoungo) | AI/추천 엔진 (ClaudeService, RecommendationService, RoadmapService) |
| BE-2 | 조재성 | [@wbfkr0980-tech](https://github.com/wbfkr0980-tech) | DB/코어 (Entity, Repository, UserService, ActivityService, PasserService) |
| BE-3 | 이지우 | [@011201Leejiwoo](https://github.com/011201Leejiwoo) | API/인프라 (Controller, Security, JWT, OAuth2, 배포) |

