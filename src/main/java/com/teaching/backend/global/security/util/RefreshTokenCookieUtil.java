package com.teaching.backend.global.security.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * refreshToken 쿠키 발급/삭제를 전담하는 컴포넌트.
 * secure/domain 등 쿠키 옵션은 이 클래스 하나에서만 관리하고,
 * 호출하는 쪽(OAuthSuccessHandler, AuthController)은 값(refreshToken, maxAge)만 넘긴다.
 *
 * cookie.domain 도입 이전에는 Domain 속성 없는 host-only 쿠키로 refreshToken을
 * 발급했었다. domain이 설정된 환경(prod)에서 재로그인/로그아웃 시 그 옛날
 * host-only 쿠키를 같이 만료시켜야, 새 Domain 쿠키와 공존하며 둘 다 요청에
 * 실려 인증이 불안정해지는 것을 막을 수 있다.
 * 기존 사용자들이 재로그인/로그아웃을 거치거나 host-only 쿠키가 자연 만료(14일)되면
 * 더 이상 필요 없어지는 임시 호환 코드이므로, 추후 제거 가능.
 */
@Component
public class RefreshTokenCookieUtil {

    private static final String COOKIE_NAME = "refreshToken";

    @Value("${cookie.secure}")
    private boolean secure;

    @Value("${cookie.domain}")
    private String domain;

    public void issue(HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(refreshToken, maxAgeSeconds).toString());
        addLegacyHostOnlyExpiryIfNeeded(response);
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", 0).toString());
        addLegacyHostOnlyExpiryIfNeeded(response);
    }

    private ResponseCookie build(String value, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(secure ? "None" : "Lax")
                .path("/")
                .maxAge(maxAgeSeconds);

        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }

        return builder.build();
    }

    /**
     * domain이 설정된 환경에서, Domain 속성 없이 발급됐던 옛날 host-only
     * refreshToken 쿠키를 즉시 만료시킨다. domain을 지정하지 않아야
     * host-only 쿠키를 정확히 타겟팅해 만료시킬 수 있다.
     */
    private void addLegacyHostOnlyExpiryIfNeeded(HttpServletResponse response) {
        if (domain == null || domain.isBlank()) {
            return;
        }

        ResponseCookie legacyExpiry = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(secure ? "None" : "Lax")
                .path("/")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, legacyExpiry.toString());
    }
}