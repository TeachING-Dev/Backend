package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.material.entity.MaterialUrlAnalysisLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface MaterialUrlAnalysisLockRepository extends JpaRepository<MaterialUrlAnalysisLock, Long> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO material_url_analysis_locks (
                        user_id,
                        url_hash,
                        original_url,
                        owner_token,
                        expires_at,
                        created_at,
                        updated_at
                    )
                    VALUES (
                        :userId,
                        :urlHash,
                        :originalUrl,
                        :ownerToken,
                        :expiresAt,
                        :now,
                        :now
                    )
                    ON DUPLICATE KEY UPDATE
                        owner_token = IF(expires_at < :now, VALUES(owner_token), owner_token),
                        expires_at = IF(expires_at < :now, VALUES(expires_at), expires_at),
                        original_url = IF(expires_at < :now, VALUES(original_url), original_url),
                        updated_at = IF(expires_at < :now, VALUES(updated_at), updated_at)
                    """,
            nativeQuery = true
    )
    int acquireOrRefreshStale(
            @Param("userId") Long userId,
            @Param("urlHash") String urlHash,
            @Param("originalUrl") String originalUrl,
            @Param("ownerToken") String ownerToken,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("now") LocalDateTime now
    );

    boolean existsByUserIdAndUrlHashAndOwnerToken(
            Long userId,
            String urlHash,
            String ownerToken
    );

    @Modifying
    @Query(
            """
                    DELETE FROM MaterialUrlAnalysisLock lock
                    WHERE lock.userId = :userId
                      AND lock.urlHash = :urlHash
                      AND lock.ownerToken = :ownerToken
                    """
    )
    int deleteOwned(
            @Param("userId") Long userId,
            @Param("urlHash") String urlHash,
            @Param("ownerToken") String ownerToken
    );
}
