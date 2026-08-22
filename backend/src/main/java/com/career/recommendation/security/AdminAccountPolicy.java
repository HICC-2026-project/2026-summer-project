package com.career.recommendation.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 어떤 계정이 관리자(검수자)인지 결정한다.
 *
 * 환경변수 ADMIN_PROVIDER_IDS="KAKAO:1234567,KAKAO:7654321" 로 지정한다. DB 마이그레이션에
 * 특정 providerId를 박지 않는 이유: 시드 SQL에 팀원의 카카오 식별자가 남고, 바꿀 때마다
 * 마이그레이션이 필요해진다. 환경변수는 배포 환경마다 다르게 둘 수 있고 코드에 흔적이 없다.
 *
 * 역할은 로그인 시점에 동기화된다(OAuth2LoginSuccessHandler.upsertUser) — 목록에서 빠지면
 * 다음 로그인부터 USER로 돌아가고, 그 사이의 액세스 토큰은 만료(1시간)까지만 유효하다.
 */
@Slf4j
@Component
public class AdminAccountPolicy {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    private final Set<String> adminKeys;

    public AdminAccountPolicy(@Value("${app.admin.provider-ids:}") String providerIds) {
        this.adminKeys = Arrays.stream(providerIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        if (adminKeys.isEmpty()) {
            log.info("관리자 계정이 설정되지 않았습니다(ADMIN_PROVIDER_IDS 비어 있음). 검수 API는 아무도 쓸 수 없습니다.");
        } else {
            log.info("관리자 계정 {}건이 설정되었습니다.", adminKeys.size());
        }
    }

    public boolean isAdmin(String provider, String providerId) {
        if (provider == null || providerId == null) {
            return false;
        }
        return adminKeys.contains((provider + ":" + providerId).toUpperCase(Locale.ROOT));
    }

    public String resolveRole(String provider, String providerId) {
        return isAdmin(provider, providerId) ? ROLE_ADMIN : ROLE_USER;
    }
}
