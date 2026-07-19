package com.teaching.backend.domain.auth.service;

import com.teaching.backend.domain.auth.exception.AuthErrorCode;
import com.teaching.backend.domain.auth.exception.AuthException;
import com.teaching.backend.global.security.entity.AuthMember;
import com.teaching.backend.global.security.util.JwtUtil;
import com.teaching.backend.domain.auth.entity.RefreshToken;
import com.teaching.backend.domain.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHasher tokenHasher;

    @Transactional
    public String reissueAccessToken(String refreshToken) {
        if (!jwtUtil.isValid(refreshToken)) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        String tokenHash = tokenHasher.hash(refreshToken);

        RefreshToken savedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (savedToken.isExpired()) {
            throw new AuthException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        AuthMember authMember = AuthMember.from(savedToken.getUser());
        return jwtUtil.createAccessToken(authMember);
    }

    /** 로그아웃: 쿠키로 전달된 refreshToken에 해당하는 row 삭제. 없거나 이미 삭제된 경우도 정상 처리(멱등). */
    @Transactional
    public void logout(String refreshToken) {
        String tokenHash = tokenHasher.hash(refreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(refreshTokenRepository::delete);
    }

    /** 회원 탈퇴 시 해당 사용자의 refreshToken을 무효화한다. */
    @Transactional
    public void revokeRefreshToken(Long userId) {
        refreshTokenRepository.deleteByUser_Id(userId);
    }
}