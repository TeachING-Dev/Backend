package com.teaching.backend.global.security.handler;

import com.teaching.backend.domain.auth.service.TokenHasher;
import com.teaching.backend.global.security.entity.AuthMember;
import com.teaching.backend.global.security.entity.OAuthMember;
import com.teaching.backend.global.security.service.RefreshTokenService;
import com.teaching.backend.global.security.util.JwtUtil;
import com.teaching.backend.domain.auth.entity.RefreshToken;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.auth.repository.RefreshTokenRepository;
import com.teaching.backend.global.security.util.RefreshTokenCookieUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.LocalDateTime;

@Component

@RequiredArgsConstructor
public class OAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookieUtil refreshTokenCookieUtil;
    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;


    private final TokenHasher tokenHasher;


    @Override
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
        LocalDateTime expiry = jwtUtil.getRefreshTokenExpiryDate();

        try {
            refreshTokenService.saveOrUpdate(user, refreshTokenHash, expiry);
        } catch (DataIntegrityViolationException e) {
            refreshTokenService.forceUpdate(user, refreshTokenHash, expiry);
        }


        refreshTokenCookieUtil.issue(response, refreshToken, 14 * 24 * 60 * 60);

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("accessToken", accessToken)
                .queryParam("isNewUser", oAuthMember.isNewUser())
                .build()
                .toUriString();

        response.sendRedirect(targetUrl);
    }
}