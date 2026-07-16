package com.teaching.backend.domain.auth.entity;

import com.teaching.backend.global.common.BaseTimeEntity;
import com.teaching.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    @Builder(access = AccessLevel.PRIVATE)
    private RefreshToken(User user, String tokenHash, LocalDateTime expiredAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiredAt = expiredAt;
    }

    public static RefreshToken create(User user, String tokenHash, LocalDateTime expiredAt) {
        return RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiredAt(expiredAt)
                .build();
    }

    public void update(String newTokenHash, LocalDateTime newExpiredAt) {
        this.tokenHash = newTokenHash;
        this.expiredAt = newExpiredAt;
    }

    public boolean isExpired() {
        return expiredAt.isBefore(LocalDateTime.now());
    }
}