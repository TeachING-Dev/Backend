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
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", 0).toString());
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
}