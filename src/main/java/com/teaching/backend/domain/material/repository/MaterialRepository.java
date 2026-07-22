package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.material.entity.Material;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialRepository extends JpaRepository<Material, Long> {

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
