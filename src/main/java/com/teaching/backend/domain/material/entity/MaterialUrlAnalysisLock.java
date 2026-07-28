package com.teaching.backend.domain.material.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "material_url_analysis_locks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_material_url_analysis_lock_user_url",
                        columnNames = {"user_id", "url_hash"}
                )
        }
)
public class MaterialUrlAnalysisLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "url_hash", nullable = false, length = 64)
    private String urlHash;

    @Column(name = "original_url", nullable = false, length = 500)
    private String originalUrl;

    @Column(name = "owner_token", nullable = false, length = 36)
    private String ownerToken;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
