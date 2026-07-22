package com.teaching.backend.global.security.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 로그아웃/회원탈퇴 시 refreshToken 쿠키를 클라이언트에서 즉시 만료시키는 공통 유틸.
 * 쿠키 옵션(HttpOnly, Path)은 OAuthSuccessHandler 가 최초 발급할 때와 동일하게 맞춘다.
 */
public class RefreshTokenCookieUtil {

    private static final String COOKIE_NAME = "refreshToken";

    public static void clear(HttpServletResponse response, boolean secure) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
