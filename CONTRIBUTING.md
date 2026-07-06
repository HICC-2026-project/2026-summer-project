# 협업 규칙 (초안 — 7/8 팀 회의에서 최종 확정 필요)

## 브랜치 전략
- `main`: 프로덕션 (항상 배포 가능한 상태 유지)
- `dev`: 개발 통합 브랜치
- `feat/{기능명}`: 기능 개발 브랜치 (예: `feat/kakao-login`, `feat/recommendation-api`)

작업 순서: `dev`에서 `feat/{기능명}` 분기 → 작업 → `dev`로 PR → 통합 테스트 후 `main`으로 PR.

## 커밋 메시지 컨벤션
기존 커밋 이력과 동일하게 [Conventional Commits](https://www.conventionalcommits.org/) 형식을 따릅니다.

```
feat: 기능 설명
fix: 버그 설명
docs: 문서 변경
chore: 빌드/설정 변경
refactor: 리팩터링
```

## PR 규칙
- PR은 팀원 최소 **1명 승인** 후 머지
- PR 설명에 변경 내용과 테스트 방법 간단히 기재
- 리뷰 응답 기대치: 24시간 이내 (디스코드로 알림)

## 보안 원칙
- API 키/시크릿은 절대 GitHub에 올리지 않는다 — `application-local.yml`로만 관리 (`.gitignore` 등록됨)
- 공용 계정·API 키는 노션 비공개 페이지 또는 디스코드/카톡 고정 메시지로만 공유
