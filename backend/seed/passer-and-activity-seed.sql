-- passer-and-activity-seed.sql
-- 10개 대표 IT 활동 및 합격자 스펙 시드 데이터 (수동 실행용)

INSERT INTO activities (id, type, name, organization, description, deadline, start_date, end_date, target_spec, tags, url, is_active)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'EDUCATION',
    '삼성 청년 SW 아카데미 (SSAFY) 12기',
    '삼성전자',
    '삼성전자와 고용노동부가 함께하는 1년 과정의 대한민국 대표 소프트웨어 역량 향상 교육 프로그램. 백엔드, 프론트엔드, 모바일 SW 트랙 운영.',
    '2026-10-31',
    '2027-01-02',
    '2027-12-31',
    '{"min_gpa": 3.0, "required_certs": ["정보처리기사"]}'::jsonb,
    ARRAY['SSAFY', '삼성', '코딩테스트', '백엔드', '프론트엔드', '부트캠프'],
    'https://www.ssafy.com',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO activities (id, type, name, organization, description, deadline, start_date, end_date, target_spec, tags, url, is_active)
VALUES (
    'a0000000-0000-0000-0000-000000000002',
    'EDUCATION',
    '우아한테크코스 7기',
    '우아한형제들',
    '우아한형제들에서 주관하는 실무 중심의 몰입형 웹 개발자 양성 교육 과정. 웹 백엔드, 웹 프론트엔드, 안드로이드 트랙 운영.',
    '2026-11-15',
    '2027-02-01',
    '2027-11-30',
    '{"min_gpa": 3.2, "required_certs": []}'::jsonb,
    ARRAY['우아한테크코스', '우테코', 'Java', 'Spring', 'React', '백엔드'],
    'https://woowacourse.github.io',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO activities (id, type, name, organization, description, deadline, start_date, end_date, target_spec, tags, url, is_active)
VALUES (
    'a0000000-0000-0000-0000-000000000003',
    'EDUCATION',
    '네이버 부스트캠프 웹·모바일 9기',
    '네이버 커넥트재단',
    '네이버 커넥트재단이 주관하는 지속 가능한 개발자 양성을 위한 맴버십/챌린지 실무 교육 프로그램.',
    '2026-09-20',
    '2026-10-01',
    '2027-02-28',
    '{"min_gpa": 3.0, "required_certs": []}'::jsonb,
    ARRAY['네이버', '부스트캠프', '웹백엔드', '웹프론트엔드', 'JavaScript', 'Node.js'],
    'https://boostcamp.connect.or.kr',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO activities (id, type, name, organization, description, deadline, start_date, end_date, target_spec, tags, url, is_active)
VALUES (
    'a0000000-0000-0000-0000-000000000004',
    'COMPETITION',
    '2026 오픈소스 개발자대회',
    '과학기술정보통신부 / NIPA',
    '국내외 오픈소스 프로젝트 참가 및 직접 오픈소스 소프트웨어를 개발하여 경쟁하는 과기정통부 주관 글로벌 경진대회.',
    '2026-09-30',
    '2026-07-01',
    '2026-10-31',
    '{"min_gpa": 3.0, "required_certs": ["정보처리기사"]}'::jsonb,
    ARRAY['오픈소스', '공모전', '해커톤', '과기정통부', 'Git', '오픈소스소프트웨어'],
    'https://www.oss.kr/pages/2',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO activities (id, type, name, organization, description, deadline, start_date, end_date, target_spec, tags, url, is_active)
VALUES (
    'a0000000-0000-0000-0000-000000000005',
    'EDUCATION',
    '소프트웨어 마에스트로 16기',
    '한국정보기술연구원(KITRI)',
    '과학기술정보통신부 지원 SW 창의인재 양성 사업. 최고급 멘토링, 개발 지원금 및 프로젝트 창업/취업 기회 제공.',
    '2026-12-10',
    '2027-01-15',
    '2027-11-30',
    '{"min_gpa": 3.5, "required_certs": ["정보처리기사", "SQLD"]}'::jsonb,
    ARRAY['소마', '소프트웨어마에스트로', '정부지원', '창업', '프로젝트', '백엔드'],
    'https://www.swmaestro.org',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO activities (id, type, name, organization, description, deadline, start_date, end_date, target_spec, tags, url, is_active)
VALUES (
    'a0000000-0000-0000-0000-000000000006',
    'INTERNSHIP',
    'ICT 학점연계 프로젝트 인턴십 2026 하반기',
    '한국정보산업연합회(FKII)',
    '대학생이 ICT 중소/중견/스타트업 기업에서 프로젝트 인턴십을 수행하고 학점을 이수하는 정부 지원 인턴십 프로그램.',
    '2026-08-25',
    '2026-09-01',
    '2026-12-31',
    '{"min_gpa": 3.3, "required_certs": ["정보처리기사"]}'::jsonb,
    ARRAY['ICT인턴십', '학점연계', '현장실습', '백엔드', '프론트엔드', '데이터'],
    'https://www.ictintern.or.kr',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO activities (id, type, name, organization, description, deadline, start_date, end_date, target_spec, tags, url, is_active)
VALUES (
    'a0000000-0000-0000-0000-000000000007',
    'EDUCATION',
    '카카오 테크 캠퍼스 3기',
    '카카오',
    '카카오가 진행하는 대학생 대상 실무형 IT 아카데미. 프론트엔드/백엔드 트랙별 프로젝트 기반 성장 프로그램.',
    '2026-10-15',
    '2026-11-01',
    '2027-03-31',
    '{"min_gpa": 3.2, "required_certs": []}'::jsonb,
    ARRAY['카카오', '카테캠', '백엔드', '프론트엔드', 'Java', 'React'],
    'https://campus.kakao.com',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO activities (id, type, name, organization, description, deadline, start_date, end_date, target_spec, tags, url, is_active)
VALUES (
    'a0000000-0000-0000-0000-000000000008',
    'COMPETITION',
    '크래프톤 x 올리브영 COFA Hackathon',
    '크래프톤 & CJ올리브영',
    'AI Native 개발 환경에서 LLM 기반 생성형 AI 기술과 서비스 아이디어를 프로토타입으로 구체화하는 해커톤 경진대회.',
    '2026-08-15',
    '2026-08-20',
    '2026-08-22',
    '{"min_gpa": 3.0, "required_certs": []}'::jsonb,
    ARRAY['해커톤', '크래프톤', '올리브영', 'AI', 'LLM', 'AI/ML'],
    'https://cofathon.getcofa.com',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO activities (id, type, name, organization, description, deadline, start_date, end_date, target_spec, tags, url, is_active)
VALUES (
    'a0000000-0000-0000-0000-000000000009',
    'EXTERNAL',
    'Google Developer Student Clubs (GDGoC) 2026-2027',
    'Google Developers',
    '구글 디벨로퍼스가 지원하는 대학생 개발자 커뮤니티. 기술 스터디, Solution Challenge 참가 및 글로벌 네트워킹.',
    '2026-08-31',
    '2026-09-01',
    '2027-08-31',
    '{"min_gpa": 3.0, "required_certs": []}'::jsonb,
    ARRAY['GDSC', 'GDGoC', 'Google', '대외활동', '스터디', 'Solution Challenge'],
    'https://gdg.community.dev',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO activities (id, type, name, organization, description, deadline, start_date, end_date, target_spec, tags, url, is_active)
VALUES (
    'a0000000-0000-0000-0000-000000000010',
    'EDUCATION',
    '현대자동차 Softeer Bootcamp 5기',
    '현대자동차그룹',
    '현대자동차그룹에서 진행하는 차량 SW 및 백엔드/클라우드 특화 무료 무상교육 및 채용 연계형 개발자 부트캠프.',
    '2026-11-30',
    '2027-01-05',
    '2027-03-30',
    '{"min_gpa": 3.5, "required_certs": ["정보처리기사", "AWS SAA"]}'::jsonb,
    ARRAY['Softeer', '현대자동차', '백엔드', '부트캠프', '채용연계', 'C++', 'Java'],
    'https://softeerbootcamp.hyundai.com',
    TRUE
) ON CONFLICT (id) DO NOTHING;

-- PasserData SQL

INSERT INTO passer_data (id, activity_id, job_type, year, gpa, language_score, certifications, experience_count, spec_summary, is_verified)
VALUES (
    'b0000000-0000-0000-0000-000000000001',
    'a0000000-0000-0000-0000-000000000001',
    'BE',
    2025,
    3.75,
    '{"toeic": 860}'::jsonb,
    ARRAY['정보처리기사', 'SQLD'],
    2,
    '컴퓨터공학과 4학년 / 백엔드 웹 프로젝트 2회 (Spring Boot, MySQL) / 정보처리기사, SQLD 보유 / 토익 860점',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO passer_data (id, activity_id, job_type, year, gpa, language_score, certifications, experience_count, spec_summary, is_verified)
VALUES (
    'b0000000-0000-0000-0000-000000000002',
    'a0000000-0000-0000-0000-000000000002',
    'BE',
    2025,
    3.65,
    '{"toeic": 830}'::jsonb,
    ARRAY['정보처리기사'],
    1,
    '소프트웨어전공 4학년 / Java 객체지향 설계 및 JUnit 테스트 작성 경험 / 알고리즘 스터디 6개월 / 정보처리기사',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO passer_data (id, activity_id, job_type, year, gpa, language_score, certifications, experience_count, spec_summary, is_verified)
VALUES (
    'b0000000-0000-0000-0000-000000000003',
    'a0000000-0000-0000-0000-000000000003',
    'FE',
    2025,
    3.82,
    '{"toeic_speaking": "AL"}'::jsonb,
    ARRAY['SQLD'],
    2,
    '컴퓨터공학과 3학년 / React, TypeScript 기반 대용량 웹 프로젝트 2회 / SQLD 보유 / 토익스피킹 AL',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO passer_data (id, activity_id, job_type, year, gpa, language_score, certifications, experience_count, spec_summary, is_verified)
VALUES (
    'b0000000-0000-0000-0000-000000000004',
    'a0000000-0000-0000-0000-000000000004',
    'BE',
    2025,
    3.9,
    '{"toeic": 910}'::jsonb,
    ARRAY['정보처리기사', 'AWS SAA'],
    3,
    '컴퓨터공학과 4학년 / 오픈소스 프로젝트 PR 기여 3회 / Docker, Kubernetes 클라우드 구축 / 정보처리기사, AWS SAA',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO passer_data (id, activity_id, job_type, year, gpa, language_score, certifications, experience_count, spec_summary, is_verified)
VALUES (
    'b0000000-0000-0000-0000-000000000005',
    'a0000000-0000-0000-0000-000000000005',
    'AI/ML',
    2025,
    4.1,
    '{"opic": "IH"}'::jsonb,
    ARRAY['정보처리기사', 'ADsP'],
    2,
    '인공지능학과 4학년 / PyTorch 기반 자연어 처리(NLP) 해커톤 수상 / ADsP, 정보처리기사 / OPIc IH',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO passer_data (id, activity_id, job_type, year, gpa, language_score, certifications, experience_count, spec_summary, is_verified)
VALUES (
    'b0000000-0000-0000-0000-000000000006',
    'a0000000-0000-0000-0000-000000000006',
    'BE',
    2025,
    3.55,
    '{"toeic": 810}'::jsonb,
    ARRAY['정보처리기사'],
    1,
    '정보통신공학과 4학년 / Node.js Express 기반 동아리 서비스 API 개발 / 정보처리기사 보유 / 토익 810점',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO passer_data (id, activity_id, job_type, year, gpa, language_score, certifications, experience_count, spec_summary, is_verified)
VALUES (
    'b0000000-0000-0000-0000-000000000007',
    'a0000000-0000-0000-0000-000000000007',
    'FE',
    2025,
    3.48,
    '{"toeic": 780}'::jsonb,
    ARRAY['WebDesign'],
    1,
    '전산학과 3학년 / Next.js & Tailwind CSS 웹 프론트엔드 제작 / 토익 780점 / 튜터링 1회',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO passer_data (id, activity_id, job_type, year, gpa, language_score, certifications, experience_count, spec_summary, is_verified)
VALUES (
    'b0000000-0000-0000-0000-000000000008',
    'a0000000-0000-0000-0000-000000000008',
    'AI/ML',
    2025,
    3.88,
    '{"opic": "AL"}'::jsonb,
    ARRAY['SQLD'],
    3,
    '컴퓨터공학과 4학년 / LangChain & Vector DB 기반 RAG 추천 시스템 구축 / 해커톤 2회 수상 / SQLD',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO passer_data (id, activity_id, job_type, year, gpa, language_score, certifications, experience_count, spec_summary, is_verified)
VALUES (
    'b0000000-0000-0000-0000-000000000009',
    'a0000000-0000-0000-0000-000000000009',
    '기획/PM',
    2025,
    3.6,
    '{"toeic": 890}'::jsonb,
    ARRAY['SQLD'],
    2,
    '경영정보학과 3학년 / IT 서비스 와이어프레임 기획 및 PM 프로젝트 2회 / SQLD 보유 / 토익 890점',
    TRUE
) ON CONFLICT (id) DO NOTHING;
INSERT INTO passer_data (id, activity_id, job_type, year, gpa, language_score, certifications, experience_count, spec_summary, is_verified)
VALUES (
    'b0000000-0000-0000-0000-000000000010',
    'a0000000-0000-0000-0000-000000000010',
    'BE',
    2025,
    3.95,
    '{"toeic": 920}'::jsonb,
    ARRAY['정보처리기사', 'SQLD', 'AWS SAA'],
    3,
    '컴퓨터공학과 4학년 / 분산 서버 및 Redis Caching 적용 토이 프로젝트 / 정보처리기사, SQLD, AWS SAA / 토익 920점',
    TRUE
) ON CONFLICT (id) DO NOTHING;
