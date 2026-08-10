# 합성 합격자 데모 시드

`passer-demo-seed.sql`은 발표와 개발 검증을 위한 **합성 데이터**입니다. 실제 개인의 합격 기록을 옮긴 자료가 아니며, 특정 기업·활동의 합격 가능성을 나타내지 않습니다.

## 구성

- 서버가 허용하는 6개 직무 코드별 10건, 총 60건
- 학점은 모두 `gpa`와 `gpa_max`를 함께 저장
- 어학은 서비스가 지원하는 `TOEIC`, `TOEFL`, `OPIC`만 사용
- 이름, 학교, 기업, 이메일, 자기소개서 등 개인을 식별할 수 있는 정보는 저장하지 않음
- `data_origin = 'DEMO'`, `is_verified = FALSE`, `activity_id = NULL`

`is_verified = FALSE`는 실제 제보 검증을 받은 기록이 아니라는 뜻입니다. 추천 조회 쿼리는 `DEMO` 출처를 개발·발표용 비교에 포함하도록 별도로 처리합니다.

## 값의 산정 원칙

공개된 잡코리아 기업별 합격 스펙의 평균 학점·TOEIC·자격증 보유 개수 범위를 현실성 점검에만 사용했습니다. 공개 페이지의 개별 합격자 행을 복사하거나 서로 조합하지 않았습니다. 직무별 각 행은 팀이 만든 가상의 값이며 모집단 통계나 합격선이 아닙니다.

참고 페이지(2026-08-08 확인):

- 파수에이아이 합격 스펙: https://m.jobkorea.co.kr/company/1715234/passavgspec
- 엔투비 합격 스펙: https://m.jobkorea.co.kr/company/1672918/passavgspec
- 잡코리아 합격 스펙 예시: https://www.jobkorea.co.kr/company/1822530/passavgspec

## 실행 전제

1. 애플리케이션을 실행해 Flyway `V18__add_passer_data_origin.sql`을 먼저 적용합니다.
2. 로컬 또는 데모 DB에서 `passer-demo-seed.sql`을 수동 실행합니다.
3. 기존 추천 캐시에 과거 비교 결과가 있으면 해당 로컬 캐시를 삭제한 뒤 추천 API를 다시 호출합니다.

운영 DB에 이 파일을 자동 적용하지 않습니다. 실제 사용자 제보 데이터가 확보되면 `USER_REPORT`로 분리하고, 동의·검증 정책을 거쳐 교체해야 합니다.
