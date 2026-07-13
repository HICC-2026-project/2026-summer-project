# Spec Road — Backend

AI 기반 커리어 추천 서비스 **Spec Road**의 Spring Boot 백엔드 프로젝트입니다.

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

## 로컬 실행

```bash
# 1. PostgreSQL 실행 후 DB 생성
# - Docker로 실행 (권장, 설치 불필요) — 호스트 포트 5433 사용 (로컬 기존 Postgres와 충돌 방지):
docker compose up -d
# - 로컬에 PostgreSQL이 이미 설치되어 있다면 (5432 사용 중):
psql -U postgres -c "CREATE DATABASE career_db;"

# 2. 시크릿 값 설정 — src/main/resources/application-local.yml 파일을
#    직접 새로 만들고 아래 4개 키에 실제 값을 채운다 (.env.sample 참고)
#    이 파일은 .gitignore 대상이라 git에 올라가지 않으며, 각자 로컬에 직접 만들어야 한다.
#    (.env 파일은 이 프로젝트에서 자동으로 읽어들이지 않으므로 사용하지 않는다)
```

```yaml
# application-local.yml 예시
CLAUDE_API_KEY: sk-ant-발급받은키
KAKAO_CLIENT_ID: 카카오_앱_REST_API_키
KAKAO_CLIENT_SECRET: 카카오_클라이언트_시크릿
DB_URL: jdbc:postgresql://localhost:5433/career_db   # docker-compose 사용 시 (로컬 설치 postgres는 5432 그대로 사용 가능)
DB_PASSWORD: 본인_postgres_비밀번호   # docker-compose 사용 시 1234
```

```bash
# 3. Spring Boot 실행 — local 프로필 필수 (없으면 시크릿 플레이스홀더를 못 찾아 부팅 실패)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# IntelliJ에서 실행할 경우: Run Configuration의 Active profiles에 local 입력
```

## API 문서
서버 실행 후 → http://localhost:8080/swagger-ui.html

## 브랜치 전략
- `main`: 프로덕션
- `dev`: 개발 통합
- `feat/{기능명}`: 기능 개발 브랜치
