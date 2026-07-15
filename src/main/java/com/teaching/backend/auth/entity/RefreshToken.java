package com.teaching.backend.auth.entity;

import com.teaching.backend.global.common.BaseTimeEntity;
import com.teaching.backend.user.entity.User;
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

    @Column(nullable = false,columnDefinition = "TEXT")
    private String token;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    @Builder(access = AccessLevel.PRIVATE)
    private RefreshToken(User user, String token, LocalDateTime expiredAt) {
        this.user = user;
        this.token = token;
        this.expiredAt = expiredAt;
    }

    public static RefreshToken create(User user, String token, LocalDateTime expiredAt) {
        return RefreshToken.builder()
                .user(user)
                .token(token)
                .expiredAt(expiredAt)
                .build();
    }

    public void update(String newToken, LocalDateTime newExpiredAt) {
        this.token = newToken;
        this.expiredAt = newExpiredAt;
    }

    public boolean isExpired() {
        return expiredAt.isBefore(LocalDateTime.now());
    }
}