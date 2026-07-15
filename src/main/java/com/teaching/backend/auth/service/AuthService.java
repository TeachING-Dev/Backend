package com.teaching.backend.auth.service;

import com.teaching.backend.auth.exception.AuthErrorCode;
import com.teaching.backend.auth.exception.AuthException;
import com.teaching.backend.global.security.entity.AuthMember;
import com.teaching.backend.global.security.util.JwtUtil;
import com.teaching.backend.auth.entity.RefreshToken;
import com.teaching.backend.user.exception.UserErrorCode;
import com.teaching.backend.user.exception.UserException;
import com.teaching.backend.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public String reissueAccessToken(String refreshToken) {
        if (!jwtUtil.isValid(refreshToken)) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        RefreshToken savedToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (savedToken.isExpired()) {
            throw new AuthException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        AuthMember authMember = AuthMember.from(savedToken.getUser());
        return jwtUtil.createAccessToken(authMember);
    }
}