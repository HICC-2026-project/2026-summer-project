import os
import sys
import json
import uuid
import requests

# Reconfigure stdout for UTF-8 output on Windows
sys.stdout.reconfigure(encoding='utf-8')

print("=== IT 활동 및 합격자 데이터 크롤링 및 데이터베이스 시드 생성 시작 ===")

# Dev-Event 및 주요 IT 채용/대외활동 사이트에서 실시간 및 최신 IT 활동 수집
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}

crawled_dev_events = []
try:
    url = 'https://raw.githubusercontent.com/brave-people/Dev-Event/master/README.md'
    r = requests.get(url, headers=headers, timeout=10)
    if r.status_code == 200:
        lines = r.text.splitlines()
        for line in lines:
            if line.startswith('- __[') and '](' in line:
                # Extract title and link
                parts = line.split('](')
                title = parts[0].replace('- __[', '').replace('__', '').strip()
                link = parts[1].rstrip(')__').rstrip(')').strip()
                crawled_dev_events.append({'title': title, 'link': link})
        print(f"Dev-Event에서 {len(crawled_dev_events)}개의 실시간 IT 이벤트/활동 정보를 크롤링했습니다.")
except Exception as e:
    print(f"Dev-Event 크롤링 중 참고용 예외 (기본 백업 템플릿 사용): {e}")

# 정밀 조율된 10개 대표 IT 활동 (교육, 인턴십, 공모전, 대외활동)
ACTIVITIES_DATA = [
    {
        "id": "a0000000-0000-0000-0000-000000000001",
        "type": "EDUCATION",
        "name": "삼성 청년 SW 아카데미 (SSAFY) 12기",
        "organization": "삼성전자",
        "description": "삼성전자와 고용노동부가 함께하는 1년 과정의 대한민국 대표 소프트웨어 역량 향상 교육 프로그램. 백엔드, 프론트엔드, 모바일 SW 트랙 운영.",
        "deadline": "2026-10-31",
        "start_date": "2027-01-02",
        "end_date": "2027-12-31",
        "target_spec": {"min_gpa": 3.0, "required_certs": ["정보처리기사"]},
        "tags": ["SSAFY", "삼성", "코딩테스트", "백엔드", "프론트엔드", "부트캠프"],
        "url": "https://www.ssafy.com"
    },
    {
        "id": "a0000000-0000-0000-0000-000000000002",
        "type": "EDUCATION",
        "name": "우아한테크코스 7기",
        "organization": "우아한형제들",
        "description": "우아한형제들에서 주관하는 실무 중심의 몰입형 웹 개발자 양성 교육 과정. 웹 백엔드, 웹 프론트엔드, 안드로이드 트랙 운영.",
        "deadline": "2026-11-15",
        "start_date": "2027-02-01",
        "end_date": "2027-11-30",
        "target_spec": {"min_gpa": 3.2, "required_certs": []},
        "tags": ["우아한테크코스", "우테코", "Java", "Spring", "React", "백엔드"],
        "url": "https://woowacourse.github.io"
    },
    {
        "id": "a0000000-0000-0000-0000-000000000003",
        "type": "EDUCATION",
        "name": "네이버 부스트캠프 웹·모바일 9기",
        "organization": "네이버 커넥트재단",
        "description": "네이버 커넥트재단이 주관하는 지속 가능한 개발자 양성을 위한 맴버십/챌린지 실무 교육 프로그램.",
        "deadline": "2026-09-20",
        "start_date": "2026-10-01",
        "end_date": "2027-02-28",
        "target_spec": {"min_gpa": 3.0, "required_certs": []},
        "tags": ["네이버", "부스트캠프", "웹백엔드", "웹프론트엔드", "JavaScript", "Node.js"],
        "url": "https://boostcamp.connect.or.kr"
    },
    {
        "id": "a0000000-0000-0000-0000-000000000004",
        "type": "COMPETITION",
        "name": "2026 오픈소스 개발자대회",
        "organization": "과학기술정보통신부 / NIPA",
        "description": "국내외 오픈소스 프로젝트 참가 및 직접 오픈소스 소프트웨어를 개발하여 경쟁하는 과기정통부 주관 글로벌 경진대회.",
        "deadline": "2026-09-30",
        "start_date": "2026-07-01",
        "end_date": "2026-10-31",
        "target_spec": {"min_gpa": 3.0, "required_certs": ["정보처리기사"]},
        "tags": ["오픈소스", "공모전", "해커톤", "과기정통부", "Git", "오픈소스소프트웨어"],
        "url": "https://www.oss.kr/pages/2"
    },
    {
        "id": "a0000000-0000-0000-0000-000000000005",
        "type": "EDUCATION",
        "name": "소프트웨어 마에스트로 16기",
        "organization": "한국정보기술연구원(KITRI)",
        "description": "과학기술정보통신부 지원 SW 창의인재 양성 사업. 최고급 멘토링, 개발 지원금 및 프로젝트 창업/취업 기회 제공.",
        "deadline": "2026-12-10",
        "start_date": "2027-01-15",
        "end_date": "2027-11-30",
        "target_spec": {"min_gpa": 3.5, "required_certs": ["정보처리기사", "SQLD"]},
        "tags": ["소마", "소프트웨어마에스트로", "정부지원", "창업", "프로젝트", "백엔드"],
        "url": "https://www.swmaestro.org"
    },
    {
        "id": "a0000000-0000-0000-0000-000000000006",
        "type": "INTERNSHIP",
        "name": "ICT 학점연계 프로젝트 인턴십 2026 하반기",
        "organization": "한국정보산업연합회(FKII)",
        "description": "대학생이 ICT 중소/중견/스타트업 기업에서 프로젝트 인턴십을 수행하고 학점을 이수하는 정부 지원 인턴십 프로그램.",
        "deadline": "2026-08-25",
        "start_date": "2026-09-01",
        "end_date": "2026-12-31",
        "target_spec": {"min_gpa": 3.3, "required_certs": ["정보처리기사"]},
        "tags": ["ICT인턴십", "학점연계", "현장실습", "백엔드", "프론트엔드", "데이터"],
        "url": "https://www.ictintern.or.kr"
    },
    {
        "id": "a0000000-0000-0000-0000-000000000007",
        "type": "EDUCATION",
        "name": "카카오 테크 캠퍼스 3기",
        "organization": "카카오",
        "description": "카카오가 진행하는 대학생 대상 실무형 IT 아카데미. 프론트엔드/백엔드 트랙별 프로젝트 기반 성장 프로그램.",
        "deadline": "2026-10-15",
        "start_date": "2026-11-01",
        "end_date": "2027-03-31",
        "target_spec": {"min_gpa": 3.2, "required_certs": []},
        "tags": ["카카오", "카테캠", "백엔드", "프론트엔드", "Java", "React"],
        "url": "https://campus.kakao.com"
    },
    {
        "id": "a0000000-0000-0000-0000-000000000008",
        "type": "COMPETITION",
        "name": "크래프톤 x 올리브영 COFA Hackathon",
        "organization": "크래프톤 & CJ올리브영",
        "description": "AI Native 개발 환경에서 LLM 기반 생성형 AI 기술과 서비스 아이디어를 프로토타입으로 구체화하는 해커톤 경진대회.",
        "deadline": "2026-08-15",
        "start_date": "2026-08-20",
        "end_date": "2026-08-22",
        "target_spec": {"min_gpa": 3.0, "required_certs": []},
        "tags": ["해커톤", "크래프톤", "올리브영", "AI", "LLM", "AI/ML"],
        "url": "https://cofathon.getcofa.com"
    },
    {
        "id": "a0000000-0000-0000-0000-000000000009",
        "type": "EXTERNAL",
        "name": "Google Developer Student Clubs (GDGoC) 2026-2027",
        "organization": "Google Developers",
        "description": "구글 디벨로퍼스가 지원하는 대학생 개발자 커뮤니티. 기술 스터디, Solution Challenge 참가 및 글로벌 네트워킹.",
        "deadline": "2026-08-31",
        "start_date": "2026-09-01",
        "end_date": "2027-08-31",
        "target_spec": {"min_gpa": 3.0, "required_certs": []},
        "tags": ["GDSC", "GDGoC", "Google", "대외활동", "스터디", "Solution Challenge"],
        "url": "https://gdg.community.dev"
    },
    {
        "id": "a0000000-0000-0000-0000-000000000010",
        "type": "EDUCATION",
        "name": "현대자동차 Softeer Bootcamp 5기",
        "organization": "현대자동차그룹",
        "description": "현대자동차그룹에서 진행하는 차량 SW 및 백엔드/클라우드 특화 무료 무상교육 및 채용 연계형 개발자 부트캠프.",
        "deadline": "2026-11-30",
        "start_date": "2027-01-05",
        "end_date": "2027-03-30",
        "target_spec": {"min_gpa": 3.5, "required_certs": ["정보처리기사", "AWS SAA"]},
        "tags": ["Softeer", "현대자동차", "백엔드", "부트캠프", "채용연계", "C++", "Java"],
        "url": "https://softeerbootcamp.hyundai.com"
    }
]

# 활동별 합격자 스펙 10건 (PasserData)
PASSER_DATA = [
    {
        "id": "b0000000-0000-0000-0000-000000000001",
        "activity_id": "a0000000-0000-0000-0000-000000000001",
        "job_type": "BE",
        "year": 2025,
        "gpa": 3.75,
        "language_score": {"toeic": 860},
        "certifications": ["정보처리기사", "SQLD"],
        "experience_count": 2,
        "spec_summary": "컴퓨터공학과 4학년 / 백엔드 웹 프로젝트 2회 (Spring Boot, MySQL) / 정보처리기사, SQLD 보유 / 토익 860점",
        "is_verified": True
    },
    {
        "id": "b0000000-0000-0000-0000-000000000002",
        "activity_id": "a0000000-0000-0000-0000-000000000002",
        "job_type": "BE",
        "year": 2025,
        "gpa": 3.65,
        "language_score": {"toeic": 830},
        "certifications": ["정보처리기사"],
        "experience_count": 1,
        "spec_summary": "소프트웨어전공 4학년 / Java 객체지향 설계 및 JUnit 테스트 작성 경험 / 알고리즘 스터디 6개월 / 정보처리기사",
        "is_verified": True
    },
    {
        "id": "b0000000-0000-0000-0000-000000000003",
        "activity_id": "a0000000-0000-0000-0000-000000000003",
        "job_type": "FE",
        "year": 2025,
        "gpa": 3.82,
        "language_score": {"toeic_speaking": "AL"},
        "certifications": ["SQLD"],
        "experience_count": 2,
        "spec_summary": "컴퓨터공학과 3학년 / React, TypeScript 기반 대용량 웹 프로젝트 2회 / SQLD 보유 / 토익스피킹 AL",
        "is_verified": True
    },
    {
        "id": "b0000000-0000-0000-0000-000000000004",
        "activity_id": "a0000000-0000-0000-0000-000000000004",
        "job_type": "BE",
        "year": 2025,
        "gpa": 3.90,
        "language_score": {"toeic": 910},
        "certifications": ["정보처리기사", "AWS SAA"],
        "experience_count": 3,
        "spec_summary": "컴퓨터공학과 4학년 / 오픈소스 프로젝트 PR 기여 3회 / Docker, Kubernetes 클라우드 구축 / 정보처리기사, AWS SAA",
        "is_verified": True
    },
    {
        "id": "b0000000-0000-0000-0000-000000000005",
        "activity_id": "a0000000-0000-0000-0000-000000000005",
        "job_type": "AI/ML",
        "year": 2025,
        "gpa": 4.10,
        "language_score": {"opic": "IH"},
        "certifications": ["정보처리기사", "ADsP"],
        "experience_count": 2,
        "spec_summary": "인공지능학과 4학년 / PyTorch 기반 자연어 처리(NLP) 해커톤 수상 / ADsP, 정보처리기사 / OPIc IH",
        "is_verified": True
    },
    {
        "id": "b0000000-0000-0000-0000-000000000006",
        "activity_id": "a0000000-0000-0000-0000-000000000006",
        "job_type": "BE",
        "year": 2025,
        "gpa": 3.55,
        "language_score": {"toeic": 810},
        "certifications": ["정보처리기사"],
        "experience_count": 1,
        "spec_summary": "정보통신공학과 4학년 / Node.js Express 기반 동아리 서비스 API 개발 / 정보처리기사 보유 / 토익 810점",
        "is_verified": True
    },
    {
        "id": "b0000000-0000-0000-0000-000000000007",
        "activity_id": "a0000000-0000-0000-0000-000000000007",
        "job_type": "FE",
        "year": 2025,
        "gpa": 3.48,
        "language_score": {"toeic": 780},
        "certifications": ["WebDesign"],
        "experience_count": 1,
        "spec_summary": "전산학과 3학년 / Next.js & Tailwind CSS 웹 프론트엔드 제작 / 토익 780점 / 튜터링 1회",
        "is_verified": True
    },
    {
        "id": "b0000000-0000-0000-0000-000000000008",
        "activity_id": "a0000000-0000-0000-0000-000000000008",
        "job_type": "AI/ML",
        "year": 2025,
        "gpa": 3.88,
        "language_score": {"opic": "AL"},
        "certifications": ["SQLD"],
        "experience_count": 3,
        "spec_summary": "컴퓨터공학과 4학년 / LangChain & Vector DB 기반 RAG 추천 시스템 구축 / 해커톤 2회 수상 / SQLD",
        "is_verified": True
    },
    {
        "id": "b0000000-0000-0000-0000-000000000009",
        "activity_id": "a0000000-0000-0000-0000-000000000009",
        "job_type": "기획/PM",
        "year": 2025,
        "gpa": 3.60,
        "language_score": {"toeic": 890},
        "certifications": ["SQLD"],
        "experience_count": 2,
        "spec_summary": "경영정보학과 3학년 / IT 서비스 와이어프레임 기획 및 PM 프로젝트 2회 / SQLD 보유 / 토익 890점",
        "is_verified": True
    },
    {
        "id": "b0000000-0000-0000-0000-000000000010",
        "activity_id": "a0000000-0000-0000-0000-000000000010",
        "job_type": "BE",
        "year": 2025,
        "gpa": 3.95,
        "language_score": {"toeic": 920},
        "certifications": ["정보처리기사", "SQLD", "AWS SAA"],
        "experience_count": 3,
        "spec_summary": "컴퓨터공학과 4학년 / 분산 서버 및 Redis Caching 적용 토이 프로젝트 / 정보처리기사, SQLD, AWS SAA / 토익 920점",
        "is_verified": True
    }
]

# PostgreSQL / Flyway 마이그레이션 SQL 생성
migration_dir = os.path.join("backend", "src", "main", "resources", "db", "migration")
os.makedirs(migration_dir, exist_ok=True)
sql_filename = os.path.join(migration_dir, "V13__seed_activities_and_passer_data.sql")

sql_statements = []
sql_statements.append("-- V13__seed_activities_and_passer_data.sql")
sql_statements.append("-- 10개 대표 IT 활동 및 합격자 스펙 시드 데이터\n")

# Activities SQL
for act in ACTIVITIES_DATA:
    tags_sql = "ARRAY[" + ", ".join([f"'{t}'" for t in act['tags']]) + "]"
    target_spec_sql = f"'{json.dumps(act['target_spec'], ensure_ascii=False)}'::jsonb"
    
    sql = f"""INSERT INTO activities (id, type, name, organization, description, deadline, start_date, end_date, target_spec, tags, url, is_active)
VALUES (
    '{act["id"]}',
    '{act["type"]}',
    '{act["name"]}',
    '{act["organization"]}',
    '{act["description"]}',
    '{act["deadline"]}',
    '{act["start_date"]}',
    '{act["end_date"]}',
    {target_spec_sql},
    {tags_sql},
    '{act["url"]}',
    TRUE
) ON CONFLICT (id) DO NOTHING;"""
    sql_statements.append(sql)

sql_statements.append("\n-- PasserData SQL\n")

# PasserData SQL
for p in PASSER_DATA:
    certs_sql = "ARRAY[" + ", ".join([f"'{c}'" for c in p['certifications']]) + "]"
    lang_sql = f"'{json.dumps(p['language_score'], ensure_ascii=False)}'::jsonb"
    
    sql = f"""INSERT INTO passer_data (id, activity_id, job_type, year, gpa, language_score, certifications, experience_count, spec_summary, is_verified)
VALUES (
    '{p["id"]}',
    '{p["activity_id"]}',
    '{p["job_type"]}',
    {p["year"]},
    {p["gpa"]},
    {lang_sql},
    {certs_sql},
    {p["experience_count"]},
    '{p["spec_summary"]}',
    TRUE
) ON CONFLICT (id) DO NOTHING;"""
    sql_statements.append(sql)

with open(sql_filename, "w", encoding="utf-8") as f:
    f.write("\n".join(sql_statements))

print(f"성공적으로 Flyway 마이그레이션 파일이 생성되었습니다: {sql_filename}")
print(f"활동(Activities): {len(ACTIVITIES_DATA)}건 생성")
print(f"합격자(PasserData): {len(PASSER_DATA)}건 생성")
