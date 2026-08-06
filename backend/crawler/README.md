# 링커리어 활동 크롤러

링커리어의 공개 공고에서 컴퓨터공학 관련 활동 후보를 수집해 JSON과 CSV 초안을 만듭니다.
Spring Boot 서버와는 별도로 실행하며 DB에는 직접 쓰지 않습니다.

## 수집 규칙

- 활동 유형: `INTERNSHIP`, `EXTERNAL`, `COMPETITION`, `EDUCATION`
- `deadline`: 링커리어의 지원/접수 마감일
- `startDate`: 링커리어의 활동기간 시작일을 우선 사용하고, 없으면 본문의 근무기간·교육기간 등에 명시된 시작일 사용
- `endDate`: 링커리어의 활동기간 종료일을 우선 사용하고, 없으면 본문의 근무기간·교육기간 등에 명시된 종료일 사용
- `url`: `홈페이지 지원`에 연결된 실제 외부 지원 페이지 우선
- `sourceUrl`: 데이터 갱신과 검수를 위한 링커리어 원본 주소
- `targetSpec.required_qualifications`: 자격요건·지원자격·필요역량 등 필수 구역의 원문 항목
- 재실행 중복 방지: `sourceUrl`로 UUID v5를 생성하므로 같은 공고는 같은 ID를 가짐

우대사항은 `targetSpec`에 저장하지 않습니다. 자격요건과 우대사항이 한 구역에 섞여 있거나
이미지로만 제공되는 경우에는 자동 추측하지 않고 검수 대상으로 남깁니다.

날짜도 원문에 정확한 날짜가 있을 때만 저장합니다. 예를 들어 `채용시 ~ 2026-12-31`은
종료일만 저장하며, `약 1년`, `6개월 진행 후 연장` 같은 표현은 임의로 시작일·종료일을
계산하지 않습니다. 이미지로만 표시된 근무기간 역시 검수 화면에서 직접 확인해야 합니다.

`urlKind` 값은 다음과 같습니다.

| 값 | 의미 |
|---|---|
| `APPLICATION` | 실제 외부 지원 페이지를 찾음 |
| `HOMEPAGE_FALLBACK` | 지원 페이지가 없어 회사/기관 홈페이지를 사용함 |
| `MISSING` | 외부 URL을 찾지 못함 |

`HOMEPAGE_FALLBACK`, `MISSING`, 날짜 누락 등이 있으면 `needsReview=true`로 표시됩니다.

## 실행 환경

Python 3.9 이상이 필요합니다. 외부 패키지는 사용하지 않습니다. `py` 명령을 찾지
못하면 [Python 공식 사이트](https://www.python.org/downloads/windows/)에서 설치하면서
`Add python.exe to PATH`를 선택한 뒤 PowerShell을 새로 여세요.

```powershell
cd "C:\Users\user\OneDrive\Desktop\2026-summer-project\2026-summer-project\backend\crawler"
py --version
```

## 방법 1: URL을 직접 골라 수집

처음에는 이 방식을 권장합니다.

```powershell
Copy-Item .\activity_urls.example.csv .\activity_urls.csv
notepad .\activity_urls.csv
py .\linkareer_crawler.py --input-csv .\activity_urls.csv --max-items 10
```

입력 형식:

```csv
type,url
INTERNSHIP,https://linkareer.com/activity/340130
EDUCATION,https://linkareer.com/activity/333927
```

`type`을 비워두면 링커리어의 공개 활동 유형 코드로 자동 분류합니다.

## 방법 2: 최신 활동에서 컴공 후보 자동 발견

```powershell
py .\linkareer_crawler.py --discover --max-items 50
```

링커리어가 공개한 최신 활동 sitemap에서 후보를 가져온 뒤 제목·본문·카테고리의 컴공
키워드 점수로 걸러냅니다. 특정 분야를 더 우선하고 싶으면 추가 키워드를 줄 수 있습니다.

```powershell
py .\linkareer_crawler.py --discover `
  --keyword "백엔드" `
  --keyword "Spring" `
  --max-items 50 `
  --max-candidates 500
```

기본적으로 요청 사이에 1.5초를 기다립니다. `--delay`는 1초 미만으로 설정할 수 없습니다.
기본 탐색 범위는 최근 후보 500개이고, 그중 조건을 통과한 활동을 최대 50개 저장합니다.
후보 페이지 응답 상태에 따라 실행에 10분 이상 걸릴 수 있습니다.

## 영구 제외 목록

검수에서 제외한 공고는 `excluded_urls.txt`에 링커리어 URL을 한 줄씩 기록합니다.
이 목록은 자동 탐색과 URL CSV 입력 방식 모두에 적용되므로, 크롤러를 다시 실행해도
동일한 공고가 결과 JSON·CSV·SQL·검수 HTML에 포함되지 않습니다.

```text
# 제외 사유는 주석으로 남길 수 있습니다.
https://linkareer.com/activity/340677
```

## 결과 파일

결과는 Git에 올라가지 않는 `output` 폴더에 생성됩니다.

```text
backend/crawler/output/linkareer-activities-YYYYMMDD-HHMMSS.json
backend/crawler/output/linkareer-activities-YYYYMMDD-HHMMSS.csv
backend/crawler/output/linkareer-activities-YYYYMMDD-HHMMSS.sql
backend/crawler/output/linkareer-activities-YYYYMMDD-HHMMSS-review.html
```

- HTML 검수 화면은 브라우저에서 표로 열리며 원본·지원 페이지 링크를 바로 확인할 수 있습니다.
- JSON/CSV에는 검수용 `sourceUrl`, `relevanceScore`, `needsReview`도 포함됩니다.
- SQL에는 현재 `Activity` 엔티티의 DB 컬럼만 들어갑니다.
- 같은 원본 공고는 항상 같은 UUID가 생성되며 SQL의 `ON CONFLICT`로 갱신됩니다.
- SQL을 만들었다는 이유만으로 DB가 바뀌지는 않습니다. 검수 후 별도로 실행해야 합니다.

DB 입력 전 다음 항목을 확인합니다.

1. `needsReview=true`인 데이터
2. `url`이 실제 지원 페이지인지
3. `deadline`, `startDate`, `endDate` 의미가 맞는지
4. 마감되거나 삭제된 공고인지
5. 컴퓨터공학 관련 활동인지
6. 같은 기관·공고가 중복되지 않았는지

## 주의

- 공개 페이지와 사이트 정책 범위 안에서만 사용합니다.
- 로그인 우회, 비공개 API 호출, CAPTCHA 우회는 하지 않습니다.
- 요청 간격을 유지하고 필요한 수량만 수집합니다.
- 사이트 구조가 변경되면 파서도 수정해야 합니다.
- 결과를 바로 운영 DB에 넣지 말고 사람이 먼저 검수합니다.
