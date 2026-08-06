from __future__ import annotations

import argparse
import csv
import html
import json
import re
import sys
import time
import uuid
from dataclasses import asdict, dataclass
from datetime import date, datetime
from html.parser import HTMLParser
from pathlib import Path
from typing import Any, Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen
from zoneinfo import ZoneInfo
from xml.etree import ElementTree


BASE_URL = "https://linkareer.com"
SEOUL = ZoneInfo("Asia/Seoul")
ALLOWED_TYPES = {"INTERNSHIP", "EXTERNAL", "COMPETITION", "EDUCATION"}
ACTIVITY_TYPE_MAP = {
    1: "EXTERNAL",
    3: "COMPETITION",
    5: "INTERNSHIP",
    6: "EDUCATION",
}

SITEMAP_INDEX_URL = f"{BASE_URL}/sitemap/sitemapindex.xml"
DEFAULT_EXCLUDE_FILE = Path(__file__).resolve().parent / "excluded_urls.txt"

# 제목은 가중치를 크게, 본문과 링커리어 카테고리는 작게 반영합니다.
CS_KEYWORDS = {
    "백엔드": 4,
    "서버 개발": 4,
    "server developer": 4,
    "프론트엔드": 4,
    "frontend": 4,
    "소프트웨어": 3,
    "software": 3,
    "프로그래밍": 3,
    "개발자": 3,
    "developer": 3,
    "인공지능": 3,
    "ai": 2,
    "머신러닝": 3,
    "machine learning": 3,
    "데이터 엔지니어": 4,
    "데이터사이언스": 3,
    "빅데이터": 2,
    "정보보안": 3,
    "사이버보안": 3,
    "클라우드": 3,
    "devops": 3,
    "java": 3,
    "spring": 3,
    "react": 3,
    "javascript": 2,
    "typescript": 2,
    "python": 2,
    "임베디드": 3,
    "iot": 2,
    "로봇": 2,
    "알고리즘": 3,
    "오픈소스": 2,
    "해커톤": 2,
    "it/개발": 3,
}

TAG_RULES = {
    "백엔드": ("백엔드", "서버 개발", "server developer", "spring", "java"),
    "프론트엔드": ("프론트엔드", "frontend", "react", "javascript", "typescript"),
    "AI": ("인공지능", " ai ", "머신러닝", "machine learning", "딥러닝"),
    "데이터": ("데이터 엔지니어", "데이터사이언스", "빅데이터", "data engineer"),
    "보안": ("정보보안", "사이버보안", "security"),
    "클라우드": ("클라우드", "cloud", "aws", "devops"),
    "임베디드": ("임베디드", "iot", "로봇", "ros2"),
    "모바일": ("안드로이드", "android", "ios", "모바일"),
    "게임": ("게임 개발", "unity", "unreal"),
    "알고리즘": ("알고리즘", "코딩테스트"),
    "오픈소스": ("오픈소스", "open source"),
    "해커톤": ("해커톤", "hackathon"),
}

START_DATE_LABELS = {
    "INTERNSHIP": ("입사 예정일", "입사예정일", "근무 시작일", "출근 예정일"),
    "EDUCATION": ("교육 시작일", "교육시작일", "개강일", "과정 시작일"),
    "COMPETITION": ("대회 시작일", "본선일", "본선 일정"),
    "EXTERNAL": ("활동 시작일", "활동시작일", "발대식"),
}

END_DATE_LABELS = {
    "INTERNSHIP": ("근무 종료일", "인턴 종료일"),
    "EDUCATION": ("교육 종료일", "교육종료일", "수료일", "과정 종료일"),
    "COMPETITION": ("대회 종료일", "결선일", "시상식"),
    "EXTERNAL": ("활동 종료일", "활동종료일", "해단식"),
}

PERIOD_LABELS = {
    "INTERNSHIP": ("근무기간", "계약기간", "인턴기간"),
    "EDUCATION": ("활동기간", "교육기간", "과정기간", "수강기간"),
    "COMPETITION": ("대회기간", "행사기간", "본선기간"),
    "EXTERNAL": ("활동기간", "행사기간", "서포터즈 활동기간"),
}

DATE_PATTERN = re.compile(
    r"(20\d{2})\s*[.\-/년]\s*(\d{1,2})\s*[.\-/월]\s*(\d{1,2})\s*일?"
)
ACTIVITY_LINK_PATTERN = re.compile(r"(?:https?://linkareer\.com)?/activity/(\d+)")
TITLE_DEADLINE_PATTERN = re.compile(r"\s*\(~?\s*\d{1,2}\s*[./-]\s*\d{1,2}\s*\)\s*$")

REQUIRED_SECTION_HEADINGS = (
    "자격요건",
    "지원자격",
    "필수요건",
    "필수조건",
    "필요역량",
    "필요역량및경험",
    "필요역량/경험",
    "필수역량",
    "필수역량및경험",
    "자격요건및필요역량",
    "지원자격및필요역량",
    "응시자격",
    "신청자격",
    "참가자격",
    "모집대상",
    "지원대상",
)

STOP_SECTION_HEADINGS = (
    "우대사항",
    "우대조건",
    "담당업무",
    "주요업무",
    "업무내용",
    "전형절차",
    "채용절차",
    "접수방법",
    "지원방법",
    "근무조건",
    "근무환경",
    "복리후생",
    "혜택",
    "활동내용",
    "교육내용",
    "교육장소",
    "훈련장소",
    "선발및수당",
    "교육혜택",
    "활동혜택",
    "모집일정",
    "모집개요",
    "모집기간",
    "접수기간",
    "신청방법",
    "문의",
    "문의사항",
    "오픈카톡",
    "네이버폼",
    "상세페이지",
    "유의사항",
)


@dataclass(frozen=True)
class Candidate:
    source_url: str
    forced_type: str | None = None


@dataclass
class CrawledActivity:
    id: str
    type: str
    name: str
    organization: str
    description: str
    deadline: str | None
    startDate: str | None
    endDate: str | None
    targetSpec: dict[str, Any]
    tags: list[str]
    url: str
    sourceUrl: str
    isActive: bool
    relevanceScore: int
    matchedKeywords: list[str]
    urlKind: str
    needsReview: bool


class ScriptCollector(HTMLParser):
    """HTML에서 JSON-LD와 __NEXT_DATA__ script 본문만 수집합니다."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=False)
        self._kind: str | None = None
        self._buffer: list[str] = []
        self.json_ld: list[str] = []
        self.next_data: str | None = None

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag.lower() != "script":
            return
        attr_map = {key.lower(): value for key, value in attrs}
        if attr_map.get("id") == "__NEXT_DATA__":
            self._kind = "next"
            self._buffer = []
        elif attr_map.get("type") == "application/ld+json":
            self._kind = "jsonld"
            self._buffer = []

    def handle_data(self, data: str) -> None:
        if self._kind is not None:
            self._buffer.append(data)

    def handle_endtag(self, tag: str) -> None:
        if tag.lower() != "script" or self._kind is None:
            return
        value = "".join(self._buffer).strip()
        if self._kind == "next":
            self.next_data = value
        elif value:
            self.json_ld.append(value)
        self._kind = None
        self._buffer = []


class TextExtractor(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.parts: list[str] = []

    def handle_data(self, data: str) -> None:
        value = data.strip()
        if value:
            self.parts.append(value)


class BlockTextExtractor(HTMLParser):
    """공고 HTML의 문단과 줄바꿈을 유지해 섹션 경계를 찾습니다."""

    BLOCK_TAGS = {"p", "li", "div", "section", "h1", "h2", "h3", "h4", "h5", "h6"}

    def __init__(self) -> None:
        super().__init__()
        self.lines: list[str] = []
        self._buffer: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag.lower() == "br":
            self._flush()

    def handle_data(self, data: str) -> None:
        if data.strip():
            self._buffer.append(data)

    def handle_endtag(self, tag: str) -> None:
        if tag.lower() in self.BLOCK_TAGS:
            self._flush()

    def close(self) -> None:
        super().close()
        self._flush()

    def _flush(self) -> None:
        text = re.sub(r"\s+", " ", " ".join(self._buffer)).strip()
        if text:
            self.lines.append(text)
        self._buffer = []


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="링커리어 공개 공고에서 컴퓨터공학 관련 활동 초안을 수집합니다."
    )
    source_group = parser.add_mutually_exclusive_group(required=True)
    source_group.add_argument(
        "--discover",
        action="store_true",
        help="링커리어 공개 sitemap에서 최신 활동 후보를 자동 발견합니다.",
    )
    source_group.add_argument(
        "--input-csv",
        type=Path,
        help="type,url 형식 CSV에 적힌 공고만 수집합니다.",
    )
    parser.add_argument(
        "--keyword",
        action="append",
        dest="keywords",
        help="컴공 관련성 판단에 추가할 키워드입니다. 여러 번 사용할 수 있습니다.",
    )
    parser.add_argument("--max-items", type=int, default=50, help="저장할 최대 활동 수 (기본 50)")
    parser.add_argument(
        "--max-candidates",
        type=int,
        default=500,
        help="상세 페이지를 확인할 최대 후보 수 (기본 500)",
    )
    parser.add_argument(
        "--min-relevance",
        type=int,
        default=4,
        help="컴공 관련성 최소 점수 (기본 4)",
    )
    parser.add_argument(
        "--delay",
        type=float,
        default=1.5,
        help="요청 사이 대기 초 (기본 1.5, 최소 1.0)",
    )
    parser.add_argument(
        "--include-expired",
        action="store_true",
        help="이미 마감된 공고도 결과에 포함합니다.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path(__file__).resolve().parent / "output",
        help="JSON/CSV/SQL/HTML 저장 폴더",
    )
    parser.add_argument(
        "--exclude-file",
        type=Path,
        default=DEFAULT_EXCLUDE_FILE,
        help="재수집하지 않을 링커리어 URL 목록 (기본 excluded_urls.txt)",
    )
    return parser.parse_args()


def fetch_html(url: str, *, timeout: int = 30, retries: int = 2) -> str:
    request = Request(
        url,
        headers={
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 Chrome/126 Safari/537.36"
            ),
            "Accept-Language": "ko-KR,ko;q=0.9,en;q=0.7",
        },
    )
    last_error: Exception | None = None
    for attempt in range(1, retries + 1):
        try:
            with urlopen(request, timeout=timeout) as response:
                charset = response.headers.get_content_charset() or "utf-8"
                return response.read().decode(charset, errors="replace")
        except (HTTPError, URLError, TimeoutError) as exc:
            last_error = exc
            if attempt < retries:
                time.sleep(attempt * 2)
    raise RuntimeError(f"페이지 요청 실패: {url} ({last_error})")


def normalize_linkareer_url(value: str) -> str | None:
    match = ACTIVITY_LINK_PATTERN.search(value)
    return f"{BASE_URL}/activity/{match.group(1)}" if match else None


def discover_candidates(max_candidates: int, delay: float) -> list[Candidate]:
    """공개 sitemap의 최신 활동부터 후보 URL을 가져옵니다."""
    index_xml = fetch_html(SITEMAP_INDEX_URL)
    sitemap_urls = {
        match.group(0)
        for match in re.finditer(
            r"https://linkareer\.com/sitemap/activities/\d+\.xml",
            index_xml,
        )
    }
    ordered_sitemaps = sorted(
        sitemap_urls,
        key=lambda value: int(re.search(r"/(\d+)\.xml$", value).group(1)),
        reverse=True,
    )
    if not ordered_sitemaps:
        raise ValueError("활동 sitemap을 찾지 못했습니다.")

    discovered: dict[str, tuple[str, Candidate]] = {}
    # 한 sitemap에는 최대 1,000건이 있으므로 최신 두 묶음까지만 확인합니다.
    for sitemap_url in ordered_sitemaps[:2]:
        print(f"[sitemap] {sitemap_url}")
        xml_text = fetch_html(sitemap_url)
        root = ElementTree.fromstring(xml_text)
        namespace = {"sm": "http://www.sitemaps.org/schemas/sitemap/0.9"}
        for node in root.findall("sm:url", namespace):
            loc = node.findtext("sm:loc", default="", namespaces=namespace)
            source_url = normalize_linkareer_url(loc)
            if source_url is None:
                continue
            lastmod = node.findtext("sm:lastmod", default="", namespaces=namespace)
            discovered[source_url] = (lastmod, Candidate(source_url))
        if len(discovered) >= max_candidates:
            break
        time.sleep(delay)

    newest_first = sorted(discovered.values(), key=lambda item: item[0], reverse=True)
    return [candidate for _, candidate in newest_first[:max_candidates]]


def read_candidates(path: Path) -> list[Candidate]:
    candidates: dict[str, Candidate] = {}
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if not reader.fieldnames or "url" not in reader.fieldnames:
            raise ValueError("입력 CSV에는 url 컬럼이 필요합니다.")
        for row in reader:
            source_url = normalize_linkareer_url((row.get("url") or "").strip())
            if source_url is None:
                print(f"[건너뜀] 잘못된 링커리어 URL: {row.get('url')}")
                continue
            forced_type = (row.get("type") or "").strip().upper() or None
            if forced_type is not None and forced_type not in ALLOWED_TYPES:
                print(f"[건너뜀] 지원하지 않는 활동 유형: {forced_type}")
                continue
            candidates[source_url] = Candidate(source_url, forced_type)
    return list(candidates.values())


def read_excluded_urls(path: Path) -> set[str]:
    """빈 줄과 # 주석을 제외하고 영구 제외할 링커리어 활동 URL을 읽습니다."""
    if not path.exists():
        return set()

    excluded: set[str] = set()
    with path.open("r", encoding="utf-8-sig") as handle:
        for raw_line in handle:
            value = raw_line.split("#", 1)[0].strip()
            if not value:
                continue
            normalized = normalize_linkareer_url(value)
            if normalized is None:
                print(f"[제외 목록 경고] 잘못된 링커리어 URL: {value}")
                continue
            excluded.add(normalized)
    return excluded


def nested_get(data: Any, *keys: str, default: Any = None) -> Any:
    current = data
    for key in keys:
        if not isinstance(current, dict) or key not in current:
            return default
        current = current[key]
    return current


def parse_page_data(page_html: str) -> tuple[dict[str, Any], dict[str, Any]]:
    collector = ScriptCollector()
    collector.feed(page_html)
    if not collector.next_data:
        raise ValueError("__NEXT_DATA__를 찾지 못했습니다.")

    next_data = json.loads(html.unescape(collector.next_data))
    page_props = nested_get(next_data, "props", "pageProps", default={})
    activity = nested_get(
        next_data,
        "props",
        "pageProps",
        "data",
        "activityData",
        "activity",
        default={},
    )
    if not isinstance(activity, dict) or not activity.get("id"):
        raise ValueError("링커리어 활동 데이터를 찾지 못했습니다.")

    # 목록/SSR 데이터에는 본문이 없고 Apollo 캐시의 ActivityText에 HTML 본문이 들어 있습니다.
    # 공개 페이지가 제공한 참조만 따라가며 별도의 비공개 API는 호출하지 않습니다.
    apollo_state = page_props.get("__APOLLO_STATE__", {}) if isinstance(page_props, dict) else {}
    if isinstance(apollo_state, dict):
        apollo_activity = apollo_state.get(f"Activity:{activity['id']}", {})
        if isinstance(apollo_activity, dict):
            for key, value in apollo_activity.items():
                if key not in activity or activity.get(key) is None:
                    activity[key] = value
            detail_ref = nested_get(apollo_activity, "detailText", "__ref")
            detail = apollo_state.get(detail_ref, {}) if isinstance(detail_ref, str) else {}
            if isinstance(detail, dict) and isinstance(detail.get("text"), str):
                activity["_detailText"] = detail["text"]

    job_posting: dict[str, Any] = {}
    for raw in collector.json_ld:
        try:
            parsed = json.loads(html.unescape(raw))
        except json.JSONDecodeError:
            continue
        values = parsed if isinstance(parsed, list) else [parsed]
        for value in values:
            if isinstance(value, dict) and value.get("@type") in {"JobPosting", "Event"}:
                job_posting = value
                break
    return activity, job_posting


def html_to_text(value: str | None) -> str:
    if not value:
        return ""
    parser = TextExtractor()
    parser.feed(html.unescape(value))
    return re.sub(r"\s+", " ", " ".join(parser.parts)).strip()


def html_to_lines(value: str | None) -> list[str]:
    if not value:
        return []
    parser = BlockTextExtractor()
    parser.feed(html.unescape(value))
    parser.close()
    return parser.lines


def normalize_section_heading(value: str) -> str:
    text = value.strip()
    text = re.sub(r"^[\[【(<]\s*", "", text)
    text = re.sub(r"\s*[\]】)>]\s*$", "", text)
    text = re.sub(r"\s+", "", text)
    return text.rstrip(":：")


def clean_requirement(value: str) -> str:
    text = value.lstrip("\ufeff\u200b")
    text = re.sub(r"^[?�]?\s*[\-–—·•▪▶※*]+", "", text).strip()
    text = re.sub(r"\([^()]*우대[^()]*\)", "", text).strip()
    if "우대" in text:
        return ""
    return re.sub(r"\s+", " ", text)[:500]


def extract_required_target_spec(detail_html: str | None) -> dict[str, Any]:
    """필수 성격의 섹션만 원문 항목으로 보존하고 우대사항은 저장하지 않습니다."""
    required: list[str] = []
    collecting = False

    for line in html_to_lines(detail_html):
        normalized = normalize_section_heading(line)

        # 자격요건과 우대사항이 하나의 제목으로 합쳐진 경우 자동 분리하지 않습니다.
        # 필수/우대 문장이 섞일 위험이 있으므로 검수 대상으로 남깁니다.
        if "우대" in normalized and any(heading in normalized for heading in REQUIRED_SECTION_HEADINGS):
            collecting = False
            continue

        required_heading = next(
            (heading for heading in REQUIRED_SECTION_HEADINGS if normalized == heading),
            None,
        )
        if required_heading:
            collecting = True
            continue

        stop_heading = normalized in STOP_SECTION_HEADINGS
        bracket_heading = bool(re.fullmatch(r"\s*[\[【].{1,40}[\]】]\s*", line))
        if stop_heading or bracket_heading:
            collecting = False
            continue

        if collecting:
            item = clean_requirement(line)
            if item and item not in required:
                required.append(item)

    if not required:
        return {}
    return {"required_qualifications": required[:20]}


def clean_title(value: str) -> str:
    title = value.split(" | 공모전 대외활동-링커리어", 1)[0].strip()
    return TITLE_DEADLINE_PATTERN.sub("", title).strip()


def epoch_ms_to_date(value: Any) -> str | None:
    if value is None:
        return None
    try:
        return datetime.fromtimestamp(float(value) / 1000, SEOUL).date().isoformat()
    except (TypeError, ValueError, OSError):
        return None


def iso_datetime_to_date(value: Any) -> str | None:
    if not isinstance(value, str) or not value:
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(SEOUL).date().isoformat()
    except ValueError:
        return None


def find_labeled_date(text: str, labels: Iterable[str]) -> str | None:
    for label in labels:
        pattern = re.compile(rf"{re.escape(label)}\s*[:：]?\s*{DATE_PATTERN.pattern}", re.IGNORECASE)
        match = pattern.search(text)
        if not match:
            continue
        try:
            return date(int(match.group(1)), int(match.group(2)), int(match.group(3))).isoformat()
        except ValueError:
            continue
    return None


def parse_exact_date(text: str) -> str | None:
    """문자열에 명시된 날짜만 ISO 날짜로 바꿉니다. 기간을 추정하지 않습니다."""
    match = DATE_PATTERN.search(text)
    if not match:
        return None
    try:
        return date(int(match.group(1)), int(match.group(2)), int(match.group(3))).isoformat()
    except ValueError:
        return None


def find_labeled_period(detail_html: str, labels: Iterable[str]) -> tuple[str | None, str | None]:
    """본문의 '활동기간: 시작 ~ 종료' 형식에서 명시된 날짜만 추출합니다.

    '채용시 ~ 2026-12-31'은 종료일만 반환하고, '약 1년'처럼 기준 날짜가
    없는 기간은 날짜로 환산하지 않습니다.
    """
    lines = html_to_lines(detail_html)
    for index, line in enumerate(lines):
        for label in labels:
            match = re.search(rf"{re.escape(label)}\s*[:：]?\s*(.*)$", line, re.IGNORECASE)
            if not match:
                continue

            value = match.group(1).strip()
            if not value and index + 1 < len(lines):
                value = lines[index + 1].strip()
            if not value:
                continue

            parts = re.split(r"\s*(?:~|∼|～)\s*", value, maxsplit=1)
            if len(parts) == 2:
                return parse_exact_date(parts[0]), parse_exact_date(parts[1])

            exact_date = parse_exact_date(value)
            if not exact_date:
                continue
            if "까지" in value or any(word in label for word in ("종료", "마감")):
                return None, exact_date
            if "부터" in value or any(word in label for word in ("시작", "입사", "출근")):
                return exact_date, None

            # '근무기간: 2026-09-01'처럼 날짜의 역할이 불분명하면 임의로
            # 시작일 또는 종료일로 정하지 않고 검수 대상으로 남깁니다.
    return None, None


def external_url(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    url = html.unescape(value).strip()
    if not url.startswith(("http://", "https://")):
        return None
    hostname = (urlparse(url).hostname or "").lower()
    return None if hostname == "linkareer.com" or hostname.endswith(".linkareer.com") else url


def choose_official_url(activity: dict[str, Any], job_posting: dict[str, Any]) -> tuple[str, str, bool]:
    application = external_url(activity.get("applyDetail"))
    if application:
        return application, "APPLICATION", False

    homepage = external_url(activity.get("homepageURL"))
    if homepage:
        return homepage, "HOMEPAGE_FALLBACK", True

    same_as = external_url(nested_get(job_posting, "hiringOrganization", "sameAs"))
    if same_as:
        return same_as, "HOMEPAGE_FALLBACK", True

    return "", "MISSING", True


def classify_activity(activity: dict[str, Any], forced_type: str | None) -> str | None:
    if forced_type:
        return forced_type
    raw_type = activity.get("activityTypeID", activity.get("type"))
    try:
        return ACTIVITY_TYPE_MAP.get(int(raw_type))
    except (TypeError, ValueError):
        return None


def category_names(activity: dict[str, Any]) -> list[str]:
    categories = activity.get("rootCategories") or []
    return [
        str(item.get("name", "")).strip()
        for item in categories
        if isinstance(item, dict) and item.get("name")
    ]


def relevance(
    title: str,
    content: str,
    categories: list[str],
    extra_keywords: Iterable[str] = (),
) -> tuple[int, list[str]]:
    title_lower = title.lower()
    body_lower = f"{content} {' '.join(categories)}".lower()
    score = 0
    matched: list[str] = []
    for keyword, weight in CS_KEYWORDS.items():
        key = keyword.lower()
        if key in title_lower:
            score += weight * 2
            matched.append(keyword)
        elif key in body_lower:
            score += weight
            matched.append(keyword)
    for keyword in extra_keywords:
        key = keyword.strip().lower()
        if not key or keyword in matched:
            continue
        if key in title_lower:
            score += 8
            matched.append(keyword)
        elif key in body_lower:
            score += 4
            matched.append(keyword)

    # "AI로 만드는 홍보 영상"처럼 AI라는 단어만 사용한 비개발 공고를 제외합니다.
    # AI 개발 공고는 머신러닝, 개발자, Python 등 다른 기술 키워드가 함께 매칭됩니다.
    if {keyword.lower() for keyword in matched} == {"ai"}:
        return 0, []
    return score, matched


def infer_tags(title: str, content: str, categories: list[str]) -> list[str]:
    haystack = f" {title} {content} {' '.join(categories)} ".lower()
    tags: list[str] = []
    for tag, needles in TAG_RULES.items():
        if any(needle.lower() in haystack for needle in needles):
            tags.append(tag)
    return tags[:8]


def build_description(activity: dict[str, Any], job_posting: dict[str, Any], content: str) -> str:
    structured = html_to_text(str(job_posting.get("description") or ""))
    # JSON-LD 설명은 종종 "상세 내용은 링커리어에서 확인" 같은 안내문뿐입니다.
    # 추천 품질에 도움이 되도록 공고 본문을 우선하고, 본문이 없을 때만 JSON-LD를 씁니다.
    value = content or structured
    return value[:1000].strip()


def crawl_candidate(candidate: Candidate, extra_keywords: Iterable[str] = ()) -> CrawledActivity:
    page_html = fetch_html(candidate.source_url)
    activity, job_posting = parse_page_data(page_html)
    activity_type = classify_activity(activity, candidate.forced_type)
    if activity_type not in ALLOWED_TYPES:
        raise ValueError(f"지원하지 않는 링커리어 활동 유형: {activity.get('activityTypeID')}")

    name = clean_title(str(activity.get("title") or job_posting.get("title") or ""))
    organization = str(
        activity.get("organizationName")
        or nested_get(job_posting, "hiringOrganization", "name")
        or ""
    ).strip()
    detail_html = str(activity.get("_detailText") or "")
    content = html_to_text(detail_html or str(activity.get("content") or ""))
    categories = category_names(activity)
    relevance_score, matched_keywords = relevance(name, content, categories, extra_keywords)
    deadline = epoch_ms_to_date(activity.get("recruitCloseAt")) or iso_datetime_to_date(
        job_posting.get("validThrough")
    )
    # 링커리어 화면의 구조화된 "활동기간"을 우선 사용합니다. 값이 없는
    # 공고만 본문의 근무기간·교육기간과 개별 시작/종료일을 보조적으로 봅니다.
    # '약 1년' 같은 기간은 실제 기준일이 없으므로 날짜로 추정하지 않습니다.
    body_start_date, body_end_date = find_labeled_period(
        detail_html, PERIOD_LABELS[activity_type]
    )
    start_date = (
        epoch_ms_to_date(activity.get("activityStartAt"))
        or body_start_date
        or find_labeled_date(content, START_DATE_LABELS[activity_type])
    )
    end_date = (
        epoch_ms_to_date(activity.get("activityEndAt"))
        or body_end_date
        or find_labeled_date(content, END_DATE_LABELS[activity_type])
    )
    official_url, url_kind, needs_url_review = choose_official_url(activity, job_posting)
    tags = infer_tags(name, content, categories)
    is_active = deadline is None or deadline >= datetime.now(SEOUL).date().isoformat()
    target_spec = extract_required_target_spec(detail_html)

    needs_review = (
        needs_url_review
        or deadline is None
        or start_date is None
        or end_date is None
        or not name
        or not organization
        or (activity_type == "INTERNSHIP" and not target_spec)
    )
    return CrawledActivity(
        id=str(uuid.uuid5(uuid.NAMESPACE_URL, candidate.source_url)),
        type=activity_type,
        name=name,
        organization=organization,
        description=build_description(activity, job_posting, content),
        deadline=deadline,
        startDate=start_date,
        endDate=end_date,
        targetSpec=target_spec,
        tags=tags,
        url=official_url,
        sourceUrl=candidate.source_url,
        isActive=is_active,
        relevanceScore=relevance_score,
        matchedKeywords=matched_keywords,
        urlKind=url_kind,
        needsReview=needs_review,
    )


def sql_literal(value: str | None) -> str:
    if value is None:
        return "NULL"
    return "'" + value.replace("'", "''") + "'"


def sql_text_array(values: list[str]) -> str:
    if not values:
        return "ARRAY[]::text[]"
    return "ARRAY[" + ", ".join(sql_literal(value) for value in values) + "]"


def write_sql(activities: list[CrawledActivity], path: Path) -> None:
    lines = [
        "-- 링커리어 크롤러가 만든 검수용 SQL입니다.",
        "-- 실행 전 원본 공고, 날짜, 지원 URL을 반드시 확인하세요.",
        "",
    ]
    if not activities:
        lines.append("-- 저장할 활동이 없습니다.")
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return

    columns = (
        "id, type, name, organization, description, deadline, start_date, end_date, "
        "target_spec, tags, url, is_active"
    )
    lines.append(f"INSERT INTO activities ({columns}) VALUES")
    value_rows: list[str] = []
    for activity in activities:
        values = [
            sql_literal(activity.id) + "::uuid",
            sql_literal(activity.type),
            sql_literal(activity.name),
            sql_literal(activity.organization),
            sql_literal(activity.description),
            sql_literal(activity.deadline) + ("::date" if activity.deadline else ""),
            sql_literal(activity.startDate) + ("::date" if activity.startDate else ""),
            sql_literal(activity.endDate) + ("::date" if activity.endDate else ""),
            sql_literal(json.dumps(activity.targetSpec, ensure_ascii=False)) + "::jsonb",
            sql_text_array(activity.tags),
            sql_literal(activity.url),
            "TRUE" if activity.isActive else "FALSE",
        ]
        value_rows.append("(\n    " + ",\n    ".join(values) + "\n)")
    lines.append(",\n".join(value_rows))
    lines.extend(
        [
            "ON CONFLICT (id) DO UPDATE SET",
            "    type = EXCLUDED.type,",
            "    name = EXCLUDED.name,",
            "    organization = EXCLUDED.organization,",
            "    description = EXCLUDED.description,",
            "    deadline = EXCLUDED.deadline,",
            "    start_date = EXCLUDED.start_date,",
            "    end_date = EXCLUDED.end_date,",
            "    target_spec = EXCLUDED.target_spec,",
            "    tags = EXCLUDED.tags,",
            "    url = EXCLUDED.url,",
            "    is_active = EXCLUDED.is_active;",
            "",
        ]
    )
    path.write_text("\n".join(lines), encoding="utf-8")


def write_review_html(activities: list[CrawledActivity], path: Path) -> None:
    def value(text: Any) -> str:
        return html.escape("" if text is None else str(text))

    rows: list[str] = []
    for index, activity in enumerate(activities, start=1):
        review_class = " review" if activity.needsReview else ""
        review_text = "확인 필요" if activity.needsReview else "기본 검수"
        application_link = (
            f'<a href="{value(activity.url)}" target="_blank" rel="noreferrer">지원 페이지</a>'
            if activity.url
            else "없음"
        )
        required_items = activity.targetSpec.get("required_qualifications", [])
        requirements = (
            "<ul>" + "".join(f"<li>{value(item)}</li>" for item in required_items) + "</ul>"
            if required_items
            else '<span class="missing">추출 없음</span>'
        )
        rows.append(
            f"""
            <tr class="{review_class.strip()}">
              <td>{index}</td>
              <td><span class="type">{value(activity.type)}</span></td>
              <td><strong>{value(activity.name)}</strong></td>
              <td>{value(activity.organization)}</td>
              <td>{value(activity.deadline) or "미정"}</td>
              <td>{value(activity.startDate) or "미정"}</td>
              <td>{value(activity.endDate) or "미정"}</td>
              <td>{value(", ".join(activity.tags)) or "-"}</td>
              <td class="requirements">{requirements}</td>
              <td>{activity.relevanceScore}</td>
              <td>{review_text}</td>
              <td>{application_link}<br><a href="{value(activity.sourceUrl)}" target="_blank" rel="noreferrer">링커리어 원본</a></td>
            </tr>
            """
        )

    empty_row = '<tr><td colspan="12" class="empty">수집된 활동이 없습니다.</td></tr>'
    document = f"""<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>링커리어 활동 검수</title>
  <style>
    body {{ font-family: Arial, "Malgun Gothic", sans-serif; margin: 24px; color: #202124; }}
    h1 {{ margin-bottom: 6px; }}
    .summary {{ color: #5f6368; margin-bottom: 18px; }}
    .table-wrap {{ overflow-x: auto; border: 1px solid #dadce0; border-radius: 10px; }}
    table {{ border-collapse: collapse; width: 100%; min-width: 1700px; }}
    th, td {{ border-bottom: 1px solid #e8eaed; padding: 11px; text-align: left; vertical-align: top; }}
    th {{ background: #f8f9fa; position: sticky; top: 0; white-space: nowrap; }}
    tr:hover {{ background: #f8fbff; }}
    tr.review {{ background: #fff7e6; }}
    .type {{ font-size: 12px; font-weight: 700; padding: 4px 7px; background: #eef2ff; border-radius: 6px; }}
    .requirements {{ min-width: 360px; }}
    .requirements ul {{ margin: 0; padding-left: 18px; }}
    .requirements li {{ margin-bottom: 5px; }}
    .missing {{ color: #b3261e; }}
    a {{ color: #315bd6; }}
    .empty {{ padding: 30px; text-align: center; color: #777; }}
  </style>
</head>
<body>
  <h1>링커리어 활동 검수</h1>
  <div class="summary">총 {len(activities)}건 · 노란 행은 추가 확인이 필요한 데이터입니다.</div>
  <div class="table-wrap">
    <table>
      <thead><tr><th>#</th><th>유형</th><th>활동명</th><th>기관</th><th>마감일</th><th>시작일</th><th>종료일</th><th>태그</th><th>필수조건</th><th>관련성</th><th>상태</th><th>링크</th></tr></thead>
      <tbody>{''.join(rows) if rows else empty_row}</tbody>
    </table>
  </div>
</body>
</html>
"""
    path.write_text(document, encoding="utf-8")


def write_results(activities: list[CrawledActivity], output_dir: Path) -> tuple[Path, Path, Path, Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    suffix = datetime.now().strftime("%Y%m%d-%H%M%S")
    json_path = output_dir / f"linkareer-activities-{suffix}.json"
    csv_path = output_dir / f"linkareer-activities-{suffix}.csv"
    sql_path = output_dir / f"linkareer-activities-{suffix}.sql"
    review_path = output_dir / f"linkareer-activities-{suffix}-review.html"
    records = [asdict(activity) for activity in activities]

    json_path.write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8")
    fieldnames = list(CrawledActivity.__dataclass_fields__.keys())
    with csv_path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for record in records:
            csv_record = record.copy()
            for key in ("targetSpec", "tags", "matchedKeywords"):
                csv_record[key] = json.dumps(csv_record[key], ensure_ascii=False)
            writer.writerow(csv_record)
    write_sql(activities, sql_path)
    write_review_html(activities, review_path)
    return json_path, csv_path, sql_path, review_path


def main() -> int:
    # Windows의 기본 콘솔 인코딩으로 표현할 수 없는 특수문자 때문에
    # 정상 공고가 실패 처리되지 않도록 출력 시 대체 문자를 허용합니다.
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(errors="replace")

    args = parse_args()
    if args.max_items <= 0 or args.max_candidates <= 0:
        print("max-items와 max-candidates는 1 이상이어야 합니다.", file=sys.stderr)
        return 2
    delay = max(1.0, args.delay)

    try:
        if args.discover:
            candidates = discover_candidates(args.max_candidates, delay)
        else:
            candidates = read_candidates(args.input_csv)
        excluded_urls = read_excluded_urls(args.exclude_file)
        before_exclusion = len(candidates)
        candidates = [
            candidate for candidate in candidates if candidate.source_url not in excluded_urls
        ]
    except Exception as exc:
        print(f"후보 URL 준비 실패: {exc}", file=sys.stderr)
        return 1

    excluded_count = before_exclusion - len(candidates)
    print(
        f"영구 제외 목록 {len(excluded_urls)}건 로드, "
        f"이번 후보에서 {excluded_count}건을 건너뜁니다."
    )
    print(f"후보 {len(candidates)}건을 확인합니다. 요청 간격: {delay:.1f}초")
    results: list[CrawledActivity] = []
    failures: list[dict[str, str]] = []

    for index, candidate in enumerate(candidates, start=1):
        if len(results) >= args.max_items:
            break
        print(f"[{index}/{len(candidates)}] {candidate.source_url}")
        try:
            activity = crawl_candidate(candidate, args.keywords or ())
            if activity.relevanceScore < args.min_relevance:
                print(f"  제외: 컴공 관련성 {activity.relevanceScore}점 | {activity.name}")
            elif not args.include_expired and not activity.isActive:
                print(f"  제외: 마감된 공고 ({activity.deadline})")
            else:
                results.append(activity)
                print(
                    f"  저장: {activity.type} | {activity.name} | "
                    f"마감 {activity.deadline or '확인 필요'} | 관련성 {activity.relevanceScore}"
                )
        except Exception as exc:
            failures.append({"sourceUrl": candidate.source_url, "error": str(exc)})
            print(f"  실패: {exc}")
        if index < len(candidates):
            time.sleep(delay)

    json_path, csv_path, sql_path, review_path = write_results(results, args.output_dir)
    if failures:
        failure_path = args.output_dir / "linkareer-failures.json"
        failure_path.write_text(json.dumps(failures, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"실패 목록: {failure_path}")

    print(f"\n완료: {len(results)}건")
    print(f"JSON: {json_path}")
    print(f"CSV : {csv_path}")
    print(f"SQL : {sql_path}")
    print(f"검수 화면: {review_path}")
    print("needsReview=true인 행은 DB 입력 전에 반드시 확인하세요.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
