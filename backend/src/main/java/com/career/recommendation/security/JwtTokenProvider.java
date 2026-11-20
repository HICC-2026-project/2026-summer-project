package com.career.recommendation.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKeyString;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey secretKey;

    /**
     * 함수 이름: init()
     * 기능: 빈 생성 직후 설정값 jwt.secret 문자열의 바이트로 HMAC-SHA 서명용 SecretKey를 만든다.
     *       키 길이가 알고리즘 최소 요건에 못 미치면 Keys.hmacShaKeyFor가 예외를 던져 기동이 실패한다.
     * 파라미터: 없음
     * 반환값: 없음
     */
    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(secretKeyString.getBytes());
    }

    /**
     * 토큰 종류 클레임. 액세스·리프레시 토큰은 서명·만료 검증만으로는 구분되지 않는다 —
     * role 클레임 유무 말고는 구조가 완전히 같아서, 리프레시 토큰을 그대로 Authorization
     * 헤더에 넣으면 JwtAuthenticationFilter가 액세스 토큰과 동일하게 인증을 통과시켰다.
     * 그러면 로그아웃(TokenService.revoke는 DB의 refresh_tokens 행만 지운다)해도, 이미
     * 발급된 리프레시 토큰 JWT 자체는 만료 시각(기본 7일)까지 모든 보호 API의 유효한
     * 베어러로 계속 쓸 수 있었다 — 액세스 토큰 만료(짧음)를 우회하는 셈이다. typ 클레임을
     * 넣고 JwtAuthenticationFilter가 "access"만 인증 처리하도록 해서 막는다.
     */
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    /**
     * 함수 이름: createAccessToken()
     * 기능: 랜덤 jti, subject=사용자 UUID, role 클레임, typ=access 클레임을 담고 액세스 토큰 만료
     *       시간(jwt.access-token-expiration)을 적용해 비밀키로 서명한 JWT를 발급한다.
     * 파라미터: UUID userId -> 토큰 주체가 될 사용자 ID
     *           String role -> 권한 문자열(ADMIN 또는 USER)
     * 반환값: 서명된 액세스 토큰 문자열
     */
    public String createAccessToken(UUID userId, String role) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("role", role)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 함수 이름: createRefreshToken()
     * 기능: 랜덤 jti, subject=사용자 UUID, typ=refresh 클레임을 담고 리프레시 토큰 만료 시간을
     *       적용해 서명한 JWT를 발급한다. role 클레임은 넣지 않으며, typ이 refresh라서
     *       JwtAuthenticationFilter에서 API 인증용으로는 통과하지 못한다.
     * 파라미터: UUID userId -> 토큰 주체가 될 사용자 ID
     * 반환값: 서명된 리프레시 토큰 문자열
     */
    public String createRefreshToken(UUID userId) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 함수 이름: getUserIdFromToken()
     * 기능: 토큰의 서명·만료를 검증하며 파싱한 뒤 subject를 UUID로 변환해 돌려준다.
     * 파라미터: String token -> JWT 문자열
     * 반환값: 토큰 주체인 사용자 UUID
     * 예외: JwtException -> 서명 불일치·만료·형식 오류일 때
     *       IllegalArgumentException -> subject가 UUID 형식이 아닐 때
     */
    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    /**
     * 함수 이름: getRoleFromToken()
     * 기능: 토큰을 검증하며 파싱해 role 클레임을 문자열로 꺼낸다(리프레시 토큰에는 role이 없어 null).
     * 파라미터: String token -> JWT 문자열
     * 반환값: role 클레임 값, 없으면 null
     * 예외: JwtException -> 서명 불일치·만료·형식 오류일 때
     */
    public String getRoleFromToken(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * 함수 이름: isAccessToken()
     * 기능: 토큰을 검증하며 파싱해 typ 클레임이 "access"인지 확인한다. 리프레시 토큰이 API 인증에
     *       쓰이는 것을 막는 핵심 검사다.
     * 파라미터: String token -> JWT 문자열
     * 반환값: typ이 access면 true, 아니면 false
     * 예외: JwtException -> 서명 불일치·만료·형식 오류일 때
     */
    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(parseClaims(token).get(CLAIM_TYPE, String.class));
    }

    /**
     * 함수 이름: isRefreshToken()
     * 기능: 토큰을 검증하며 파싱해 typ 클레임이 "refresh"인지 확인한다. 재발급 시 액세스 토큰을
     *       리프레시 토큰 자리에 넣는 것을 막는 데 쓰인다.
     * 파라미터: String token -> JWT 문자열
     * 반환값: typ이 refresh면 true, 아니면 false
     * 예외: JwtException -> 서명 불일치·만료·형식 오류일 때
     */
    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(parseClaims(token).get(CLAIM_TYPE, String.class));
    }

    /**
     * 함수 이름: getRefreshTokenExpiration()
     * 기능: 설정값(jwt.refresh-token-expiration)의 리프레시 토큰 유효 기간을 그대로 돌려준다.
     * 파라미터: 없음
     * 반환값: 리프레시 토큰 만료까지의 시간(밀리초)
     */
    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    /**
     * 함수 이름: validateToken()
     * 기능: 비밀키로 서명을 검증하고 만료·형식 오류가 없는지 확인한다. 실패하면 예외를 던지는 대신
     *       경고 로그를 남기고 false를 돌려준다. 토큰 종류(access/refresh)는 구분하지 않는다.
     * 파라미터: String token -> 검증할 JWT 문자열
     * 반환값: 유효하면 true, JwtException·IllegalArgumentException 발생 시 false
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 함수 이름: parseClaims()
     * 기능: 비밀키로 서명을 검증하면서 서명된 JWT를 파싱해 클레임(payload)을 꺼낸다.
     *       서명 불일치·만료·형식 오류는 예외로 그대로 전파된다.
     * 파라미터: String token -> JWT 문자열
     * 반환값: 검증을 통과한 Claims
     * 예외: JwtException -> 서명 불일치·만료·형식 오류일 때
     */
    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(secretKey).build()
                .parseSignedClaims(token).getPayload();
    }
}
