package com.teaching.backend.global.security.handler;

import com.teaching.backend.domain.auth.service.TokenHasher;
import com.teaching.backend.global.security.entity.AuthMember;
import com.teaching.backend.global.security.entity.OAuthMember;
import com.teaching.backend.global.security.util.JwtUtil;
import com.teaching.backend.domain.auth.entity.RefreshToken;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.auth.repository.RefreshTokenRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component

@RequiredArgsConstructor
public class OAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    @Value("${cookie.secure}")
    private boolean cookieSecure;

    private final TokenHasher tokenHasher;


    @Override
    @Transactional
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        OAuthMember oAuthMember = (OAuthMember) authentication.getPrincipal();
        User user = oAuthMember.getUser();
        AuthMember authMember = AuthMember.from(user);

        String accessToken = jwtUtil.createAccessToken(authMember);
        String refreshToken = jwtUtil.createRefreshToken(authMember);
        String refreshTokenHash = tokenHasher.hash(refreshToken);

        // 기존 refreshToken 있으면 갱신, 없으면 생성 (Rotate)
        refreshTokenRepository.findByUser(user)
                .ifPresentOrElse(
                        existing -> existing.update(refreshTokenHash, jwtUtil.getRefreshTokenExpiryDate()),  // ← 변경
                        () -> refreshTokenRepository.save(
                                RefreshToken.create(user, refreshTokenHash, jwtUtil.getRefreshTokenExpiryDate())  // ← 변경
                        )
                );

        // refreshToken은 HttpOnly 쿠키로
        Cookie refreshCookie = new Cookie("refreshToken", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(cookieSecure); // 로컬 http 테스트 시엔 false로 잠깐 바꿔야 할 수도 있음
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(14 * 24 * 60 * 60); // 14일 (초 단위)
        response.addCookie(refreshCookie);

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("accessToken", accessToken)
                .build()
                .toUriString();

        response.sendRedirect(targetUrl);
    }
}