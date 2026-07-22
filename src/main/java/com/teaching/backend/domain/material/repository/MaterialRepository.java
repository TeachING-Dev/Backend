package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.material.entity.Material;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    @Query(
            value = "SELECT * FROM materials WHERE user_id = :userId AND deleted_at IS NOT NULL ORDER BY deleted_at DESC",
            nativeQuery = true
    )
    List<Material> findTrashedByUserIdOrderByDeletedAtDesc(@Param("userId") Long userId);

    @Query(
            value = "SELECT * FROM materials WHERE user_id = :userId AND deleted_at IS NOT NULL ORDER BY deleted_at ASC",
            nativeQuery = true
    )
    List<Material> findTrashedByUserIdOrderByDeletedAtAsc(@Param("userId") Long userId);

    @Query(
            value = "SELECT COUNT(*) FROM materials WHERE id = :materialId AND user_id = :userId",
            nativeQuery = true
    )
    long countByIdAndUserIdIncludingDeleted(
            @Param("materialId") Long materialId,
            @Param("userId") Long userId
    );

    @Query(
            value = "SELECT COUNT(*) FROM materials WHERE id = :materialId AND user_id = :userId AND deleted_at IS NOT NULL",
            nativeQuery = true
    )
    long countDeletedByIdAndUserId(
            @Param("materialId") Long materialId,
            @Param("userId") Long userId
    );

    @Query(
            value = """
                    SELECT COUNT(*) FROM materials m
                    JOIN folders f ON f.id = m.folder_id
                    WHERE m.id = :materialId AND m.user_id = :userId AND f.deleted_at IS NOT NULL
                    """,
            nativeQuery = true
    )
    long countWithTrashedParentFolder(
            @Param("materialId") Long materialId,
            @Param("userId") Long userId
    );

    @Modifying
    @Query(
            value = "UPDATE materials SET deleted_at = NULL WHERE id = :materialId AND user_id = :userId AND deleted_at IS NOT NULL",
            nativeQuery = true
    )
    int restoreDeletedMaterial(
            @Param("materialId") Long materialId,
            @Param("userId") Long userId
    );

    @Query(
            value = """
                    SELECT DISTINCT m
                    FROM Material m
                    LEFT JOIN MaterialAnalysis ma ON ma.material = m
                    WHERE m.folder.id = :folderId
                      AND m.user.id = :userId
                      AND (
                          :keyword IS NULL
                          OR LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(CAST(ma.summary AS string)) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      )
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT m.id)
                    FROM Material m
                    LEFT JOIN MaterialAnalysis ma ON ma.material = m
                    WHERE m.folder.id = :folderId
                      AND m.user.id = :userId
                      AND (
                          :keyword IS NULL
                          OR LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(CAST(ma.summary AS string)) LIKE LOWER(CONCAT('%', :keyword, '%'))
                      )
                    """
    )
    Page<Material> searchFolderMaterials(
            @Param("folderId") Long folderId,
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
