# 🚀 AWS 배포 가이드 (EC2 + RDS)

> BE-3 담당. 로드맵의 "7월 중순 조기 배포" 항목 — Hello World 수준이라도 먼저 올려서 인프라를 검증하는 것이 목적.
> 순서대로 따라 하면 됨. 예상 소요: 반나절.

## 전체 구성

```
[사용자] → EC2 (Docker: Spring Boot :8080) → RDS (PostgreSQL 16)
```

- EC2 1대에 Docker로 앱 실행 (`docker-compose.prod.yml`)
- DB는 RDS PostgreSQL (앱과 같은 VPC, 외부 비공개)
- 프리티어 기준: EC2 t3.micro + RDS db.t4g.micro

---

## 1. RDS 만들기 (PostgreSQL)

AWS 콘솔 → RDS → 데이터베이스 생성

| 항목 | 값 |
|---|---|
| 엔진 | PostgreSQL 16 |
| 템플릿 | **프리 티어** |
| DB 인스턴스 식별자 | `spec-road-db` |
| 마스터 사용자 이름 | `postgres` |
| 마스터 암호 | 직접 생성 (팀 비공개 채널로만 공유) |
| 인스턴스 클래스 | db.t4g.micro |
| 스토리지 | 20GB gp3, 자동 조정 끄기 |
| 퍼블릭 액세스 | **아니요** (EC2에서만 접근) |
| 초기 데이터베이스 이름 | `career_db` ← **추가 구성에서 꼭 입력** (안 하면 DB가 안 만들어짐) |

생성 후 **엔드포인트** 복사해두기 (예: `spec-road-db.xxxx.ap-northeast-2.rds.amazonaws.com`)

## 2. EC2 만들기

AWS 콘솔 → EC2 → 인스턴스 시작

| 항목 | 값 |
|---|---|
| AMI | Ubuntu Server 24.04 LTS |
| 인스턴스 유형 | t3.micro (프리 티어) |
| 키 페어 | 새로 생성 → `.pem` 파일 안전하게 보관 |
| 보안 그룹 (인바운드) | SSH 22 (내 IP), HTTP 80 (전체), 커스텀 TCP 8080 (전체 — 초기 테스트용) |

### 보안 그룹 연결 (중요)

RDS의 보안 그룹 → 인바운드 규칙 편집 → PostgreSQL(5432)의 소스로 **EC2의 보안 그룹**을 지정.
(IP가 아니라 보안 그룹을 소스로 지정해야 EC2가 재시작돼도 연결이 유지됨)

## 3. EC2 초기 세팅

```bash
ssh -i spec-road.pem ubuntu@<EC2 퍼블릭 IP>

# Docker 설치
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit   # 그룹 적용 위해 재접속

# 스왑 2GB 추가 (t3.micro는 RAM 1GB라 빌드 중 OOM 방지에 필수)
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 4. 코드 받고 환경 변수 설정

```bash
git clone https://github.com/HICC-2026-project/<repo이름>.git
cd <repo이름>/backend

cp .env.sample .env
nano .env
```

`.env`에서 바꿀 값:

```bash
DB_URL=jdbc:postgresql://<RDS엔드포인트>:5432/career_db
DB_USERNAME=postgres
DB_PASSWORD=<RDS 마스터 암호>
JWT_SECRET=<32자 이상 랜덤 문자열>          # 생성: openssl rand -base64 48
KAKAO_CLIENT_ID=<카카오 앱 REST API 키>
KAKAO_CLIENT_SECRET=<카카오 앱 시크릿>
CLAUDE_API_KEY=<팀 공용 키>
FRONTEND_REDIRECT_URI=http://<EC2 퍼블릭 IP>:3000/oauth/callback   # 프론트 배포 전이면 일단 이대로
CORS_ALLOWED_ORIGINS=http://localhost:3000,https://*.vercel.app     # 프론트 배포 후 실제 도메인 추가
```

## 5. 실행

```bash
docker compose -f docker-compose.prod.yml up -d --build

# 확인
docker logs -f spec-road-api        # "Started CareerRecommendationApplication" 뜨면 성공
curl http://localhost:8080/api-docs # JSON 나오면 성공
```

브라우저에서 `http://<EC2 퍼블릭 IP>:8080/swagger-ui.html` 열리면 배포 완료 🎉

## 6. 카카오 개발자 콘솔 설정 추가

[developers.kakao.com](https://developers.kakao.com) → 내 애플리케이션 → 카카오 로그인 → Redirect URI에 추가:

```
http://<EC2 퍼블릭 IP>:8080/login/oauth2/code/kakao
```

(로컬용 `http://localhost:8080/login/oauth2/code/kakao`는 그대로 두고 **추가**)

## 7. 재배포 (코드 바뀌었을 때)

```bash
cd <repo이름>/backend
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

> 이 부분은 이후 GitHub Actions CI/CD(작업 #17)로 자동화 예정.

---

## 트러블슈팅

| 증상 | 원인/해결 |
|---|---|
| 앱이 DB 연결 실패로 죽음 | RDS 보안 그룹 인바운드에 EC2 보안 그룹이 소스로 등록됐는지 확인 |
| 빌드 중 EC2가 멈춤 | 스왑 미설정 — 3번 단계의 스왑 추가 후 재시도 |
| 카카오 로그인 후 redirect_uri mismatch | 카카오 콘솔의 Redirect URI와 EC2 주소가 정확히 일치하는지 (http/https, 포트 포함) 확인 |
| Flyway 마이그레이션 실패 | RDS의 `career_db`가 비어 있는 새 DB인지 확인. 수동으로 테이블 만들었다면 삭제 후 재시작 |

## 다음 단계 (배포 이후)

- [ ] 탄력적 IP 할당 (EC2 재시작 시 IP 변경 방지) — 무료지만 인스턴스에 연결된 상태여야 함
- [ ] nginx + HTTPS (카카오 로그인은 https 권장, 최종 시연 전까지)
- [ ] GitHub Actions CI/CD (#17)
- [ ] 프론트 배포 후 `FRONTEND_REDIRECT_URI` 실제 주소로 변경
