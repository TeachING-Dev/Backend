package com.teaching.backend.global.security.service;

import com.teaching.backend.domain.auth.entity.RefreshToken;
import com.teaching.backend.domain.auth.repository.RefreshTokenRepository;
import com.teaching.backend.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void saveOrUpdate(User user, String tokenHash, LocalDateTime expiry) {
        refreshTokenRepository.findByUser(user)
                .ifPresentOrElse(
                        existing -> existing.update(tokenHash, expiry),
                        () -> refreshTokenRepository.save(RefreshToken.create(user, tokenHash, expiry))
                );
    }

    // REQUIRES_NEW: 바깥 트랜잭션이 rollback-only여도 이건 새 트랜잭션이라 영향 안 받음
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forceUpdate(User user, String tokenHash, LocalDateTime expiry) {
        refreshTokenRepository.findByUser(user)
                .ifPresent(existing -> existing.update(tokenHash, expiry));
    }
}