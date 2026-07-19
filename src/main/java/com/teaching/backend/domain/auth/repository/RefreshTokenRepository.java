package com.teaching.backend.domain.auth.repository;

import com.teaching.backend.domain.auth.entity.RefreshToken;
import com.teaching.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    Optional<RefreshToken> findByUser(User user);
    void deleteByUser_Id(Long userId);
}
