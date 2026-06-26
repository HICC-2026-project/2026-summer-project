# 커리어 추천 서비스 — Backend

AI 기반 커리어 추천 서비스의 Spring Boot 백엔드 프로젝트입니다.

## 기술 스택
- Java 17 / Spring Boot 3.2
- PostgreSQL 15 / Spring Data JPA / Flyway
- Spring Security + JWT + OAuth2 (카카오·구글)
- Claude API (Anthropic) — 활동 추천 및 로드맵 생성
- SpringDoc (Swagger UI)

## 팀 역할
| 역할 | 담당 |
|---|---|
| BE-1 | AI/추천 엔진 (ClaudeService, RecommendationService, RoadmapService) |
| BE-2 | DB/코어 (Entity, Repository, UserService, ActivityService, PasserService) |
| BE-3 | API/인프라 (Controller, Security, JWT, OAuth2, 배포) |

## 로컬 실행

```bash
# 1. 환경변수 설정
cp .env.sample .env
# .env 파일에 실제 값 입력

# 2. PostgreSQL 실행
createdb career_db

# 3. Spring Boot 실행 (IntelliJ 또는)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## API 문서
서버 실행 후 → http://localhost:8080/swagger-ui.html

## 브랜치 전략
- `main`: 프로덕션
- `dev`: 개발 통합
- `feat/{기능명}`: 기능 개발 브랜치
