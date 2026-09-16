package com.career.recommendation.util;

import com.career.recommendation.exception.GithubUsernameInvalidException;

import java.util.regex.Pattern;

/**
 * "github.com/{user}" 형태의 URL 또는 순수 username 입력에서 username을 뽑아내고
 * GitHub username 규칙(영숫자·하이픈, 하이픈 연속·시작·끝 불가, ≤39자)으로 검증한다.
 * 컨트롤러 진입 시 동기적으로 호출된다 — 실제 GitHub 계정 존재 여부는 비동기 분석에서 확인한다.
 */
public final class GithubUsernameParser {

    // github.com/{user} 뒤에 슬래시·쿼리스트링이 더 있어도(레포 경로 등) username만 취한다.
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(?:https?://)?(?:www\\.)?github\\.com/([^/?#\\s]+).*$",
            Pattern.CASE_INSENSITIVE
    );

    // 영숫자·하이픈만 허용, 하이픈으로 시작/끝 불가, 하이픈 연속 불가, 최대 39자.
    private static final Pattern USERNAME_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$"
    );

    private GithubUsernameParser() {
    }

    public static String parse(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            throw new GithubUsernameInvalidException("GitHub 아이디 또는 URL을 입력해주세요.");
        }
        String trimmed = rawInput.trim();

        java.util.regex.Matcher urlMatcher = URL_PATTERN.matcher(trimmed);
        String candidate = urlMatcher.matches() ? urlMatcher.group(1) : trimmed;

        if (!USERNAME_PATTERN.matcher(candidate).matches()) {
            throw new GithubUsernameInvalidException("올바른 GitHub 아이디 형식이 아닙니다.");
        }
        return candidate;
    }
}
