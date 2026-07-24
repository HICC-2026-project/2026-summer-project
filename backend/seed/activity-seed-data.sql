-- 컴퓨터공학 전공자를 위한 활동 시드 데이터
-- 작성 기준일: 2026-07-24
--
-- type:
--   INTERNSHIP  인턴
--   EDUCATION   교육
--   COMPETITION 공모전
--   EXTERNAL    대외활동
--
-- 정확한 날짜가 공개되지 않았거나 "채용 시 마감"인 경우 NULL로 저장한다.
-- 이 파일은 Flyway 마이그레이션이 아니며, 필요한 DB에서 직접 실행한다.

INSERT INTO activities (
    id,
    type,
    name,
    organization,
    description,
    deadline,
    start_date,
    end_date,
    target_spec,
    tags,
    url,
    is_active
) VALUES
-- 인턴 1
(
    '00000000-0000-0000-0000-000000001001',
    'INTERNSHIP',
    '글로벌 채용전환형 인턴십 (제조솔루션)',
    '현대자동차',
    '제조 현장의 AI, 디지털 트윈, 로보틱스 및 제조 소프트웨어 관련 업무를 경험하는 채용전환형 인턴십이다.',
    NULL,
    NULL,
    NULL,
    '{"major": ["컴퓨터공학", "소프트웨어공학", "인공지능", "로봇공학"]}'::jsonb,
    ARRAY['AI', '디지털트윈', '로보틱스', '제조SW'],
    'https://linkareer.com/activity/337586',
    TRUE
),
-- 인턴 2
(
    '00000000-0000-0000-0000-000000001002',
    'INTERNSHIP',
    '[AI팀] 인턴 채용',
    '에프앤가이드',
    'Python 데이터 분석과 LangChain·LangGraph를 활용해 금융 리포트 테마 에이전트를 개발하는 AI 인턴 채용이다.',
    NULL,
    NULL,
    NULL,
    '{"major": ["컴퓨터공학", "소프트웨어공학", "인공지능", "데이터사이언스"]}'::jsonb,
    ARRAY['Python', 'LLM', 'LangChain', 'LangGraph', 'RAG', 'VectorDB'],
    'https://linkareer.com/activity/335949',
    TRUE
),
-- 인턴 3
(
    '00000000-0000-0000-0000-000000001003',
    'INTERNSHIP',
    'SS&C 블루프리즘 엔터프라이즈 AI 에이전트 개발 인턴',
    '블루프리즘 코리아',
    '기업용 AI 에이전트와 RPA 자동화 솔루션 개발을 경험하는 2개월 인턴십이다.',
    '2026-08-14',
    NULL,
    NULL,
    '{"major": ["컴퓨터공학", "소프트웨어공학", "인공지능"]}'::jsonb,
    ARRAY['AI에이전트', 'RPA', 'OCR', '머신러닝', '자동화'],
    'https://linkareer.com/activity/336003',
    TRUE
),
-- 교육 1
(
    '00000000-0000-0000-0000-000000001004',
    'EDUCATION',
    'AI 기반 소프트웨어 의료기기(SaMD) 모델 구축',
    '한국표준협회',
    'AI를 기반으로 소프트웨어 의료기기 모델을 구축하는 온라인 실무 교육 과정이다.',
    '2026-07-26',
    '2026-07-27',
    '2026-08-14',
    '{"major": ["컴퓨터공학", "소프트웨어공학", "인공지능", "의공학"]}'::jsonb,
    ARRAY['AI', 'SaMD', '의료AI', '모델개발', '온라인교육'],
    'https://linkareer.com/activity/333927',
    TRUE
),
-- 교육 2
(
    '00000000-0000-0000-0000-000000001005',
    'EDUCATION',
    'NVIDIA·FURIOSA 채용연계 AI 12주 실무과정',
    '한컴이노스트림',
    '딥러닝, RAG, AI 에이전트와 데이터 엔지니어링을 프로젝트 중심으로 학습하는 채용연계 교육 과정이다.',
    '2026-07-26',
    '2026-08-09',
    '2026-11-06',
    '{"major": ["컴퓨터공학", "소프트웨어공학", "인공지능", "데이터사이언스"]}'::jsonb,
    ARRAY['NVIDIA', 'FURIOSA', '딥러닝', 'RAG', 'AI에이전트', '데이터엔지니어링'],
    'https://linkareer.com/activity/333292',
    TRUE
),
-- 교육 3
(
    '00000000-0000-0000-0000-000000001006',
    'EDUCATION',
    'Physical AI 로봇 SW개발 채용연계과정',
    '대한상공회의소 서울기술교육센터',
    'ROS2와 로봇 제어, Physical AI 프로젝트를 학습하는 전액 무료 K-Digital Training 과정이다.',
    '2026-08-21',
    '2026-08-31',
    '2027-03-31',
    '{"major": ["컴퓨터공학", "소프트웨어공학", "인공지능", "로봇공학", "전자공학"]}'::jsonb,
    ARRAY['PhysicalAI', 'ROS2', '로봇SW', '로봇제어', 'KDT'],
    'https://linkareer.com/activity/334015',
    TRUE
),
-- 공모전 1
(
    '00000000-0000-0000-0000-000000001007',
    'COMPETITION',
    '전국민 AI 에이전트 챌린지 MABC 2026',
    '업스테이지',
    '개인 또는 팀으로 AI 에이전트 아이디어를 구현하고 결과물을 제출하는 전국 단위 챌린지다.',
    '2026-08-31',
    NULL,
    NULL,
    '{"major": ["컴퓨터공학", "소프트웨어공학", "인공지능", "데이터사이언스"]}'::jsonb,
    ARRAY['AI에이전트', 'Upstage', '해커톤', '프로젝트'],
    'https://linkareer.com/activity/336875',
    TRUE
),
-- 공모전 2
(
    '00000000-0000-0000-0000-000000001008',
    'COMPETITION',
    '제2회 TRAITHON 세계 최초 AI 신뢰성 해커톤',
    '씽크포비엘',
    'AI 기획자, 데이터 과학자와 AI 엔지니어가 팀을 구성해 AI 신뢰성 문제를 해결하는 해커톤이다.',
    '2026-08-21',
    NULL,
    NULL,
    '{"major": ["컴퓨터공학", "소프트웨어공학", "인공지능", "데이터사이언스"]}'::jsonb,
    ARRAY['AI신뢰성', '해커톤', '데이터사이언스', 'AI엔지니어링'],
    'https://linkareer.com/activity/335333',
    TRUE
),
-- 공모전 3
(
    '00000000-0000-0000-0000-000000001009',
    'COMPETITION',
    '제24회 임베디드SW경진대회 자유공모 부문',
    '한국임베디드AX산업협회',
    '자유 주제로 임베디드 소프트웨어 기반의 창의적인 제품 또는 서비스를 개발하는 경진대회다.',
    '2026-09-03',
    NULL,
    NULL,
    '{"major": ["컴퓨터공학", "소프트웨어공학", "임베디드시스템", "전자공학"]}'::jsonb,
    ARRAY['임베디드SW', 'IoT', '펌웨어', '경진대회'],
    'https://linkareer.com/activity/334394',
    TRUE
),
-- 대외활동 1
(
    '00000000-0000-0000-0000-000000001010',
    'EXTERNAL',
    'AI Genius 대학생 서포터즈 16기',
    'LG CNS',
    '전국 중학교의 AI·SW 체험 수업 운영을 지원하고 관련 콘텐츠를 제작하는 대학생 서포터즈 활동이다.',
    '2026-08-07',
    NULL,
    NULL,
    '{"major": ["컴퓨터공학", "소프트웨어공학", "인공지능"], "grade": [1, 2, 3, 4]}'::jsonb,
    ARRAY['AI교육', 'SW교육', '서포터즈', '멘토링', '콘텐츠제작'],
    'https://linkareer.com/activity/334965',
    TRUE
),
-- 대외활동 2
(
    '00000000-0000-0000-0000-000000001011',
    'EXTERNAL',
    '국가통합바이오빅데이터구축사업 대학생 서포터즈 2기',
    '국가통합바이오빅데이터구축사업단',
    '국가 바이오 빅데이터 구축사업을 알리는 콘텐츠와 홍보 활동에 참여하는 대학생 서포터즈다.',
    '2026-08-12',
    NULL,
    NULL,
    '{"major": ["컴퓨터공학", "소프트웨어공학", "데이터사이언스", "생명정보학"], "grade": [1, 2, 3, 4]}'::jsonb,
    ARRAY['바이오빅데이터', '데이터', '서포터즈', '콘텐츠제작'],
    'https://linkareer.com/activity/337519',
    TRUE
)
ON CONFLICT (id) DO UPDATE SET
    type = EXCLUDED.type,
    name = EXCLUDED.name,
    organization = EXCLUDED.organization,
    description = EXCLUDED.description,
    deadline = EXCLUDED.deadline,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date,
    target_spec = EXCLUDED.target_spec,
    tags = EXCLUDED.tags,
    url = EXCLUDED.url,
    is_active = EXCLUDED.is_active;
