package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialHighlight;
import com.teaching.backend.domain.material.enums.AiStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    List<Material> findAllByUser_Id(Long userId, Sort sort);

    Page<Material> findAllByUser_Id(Long userId, Pageable pageable);

    Page<Material> findAllByUser_IdAndFolderIsNotNull(Long userId, Pageable pageable);

    @Query("""
            SELECT m FROM Material m
            JOIN m.folder f
            WHERE m.user.id = :userId
              AND m.aiStatus = :aiStatus
              AND m.deletedAt IS NULL
              AND f.deletedAt IS NULL
            """)
    Page<Material> findHomeRecentMaterials(
            @Param("userId") Long userId,
            @Param("aiStatus") AiStatus aiStatus,
            Pageable pageable
    );

    List<Material> findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(
            Long userId,
            String originalUrl
    );

    Optional<Material> findByIdAndFolder_IdAndUser_Id(
            Long id,
            Long folderId,
            Long userId
    );

    Optional<Material> findByIdAndUser_Id(
            Long id,
            Long userId
    );

    List<Material> findAllByIdInAndFolder_IdAndUser_Id(
            List<Long> ids,
            Long folderId,
            Long userId
    );

    @Modifying
    @Query(
            value = """
                    UPDATE materials
                    SET deleted_at = NULL,
                        folder_id = :folderId,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id IN (:materialIds)
                      AND user_id = :userId
                      AND deleted_at IS NOT NULL
                    """,
            nativeQuery = true
    )
    int restoreDeletedMaterials(
            @Param("materialIds") List<Long> materialIds,
            @Param("folderId") Long folderId,
            @Param("userId") Long userId
    );

    /** 자료 자신이 휴지통 상태이면서, 상위 폴더는 휴지통이 아닌(또는 폴더가 없는) 것만 노출한다.
     *  폴더 삭제로 함께 휴지통 처리된 자료는 폴더 휴지통 목록에만 나타나야 하므로 여기서 제외한다. */
    @Query(
            value = """
                    SELECT m.* FROM materials m
                    LEFT JOIN folders f ON f.id = m.folder_id
                    WHERE m.user_id = :userId
                      AND m.deleted_at IS NOT NULL
                      AND (m.folder_id IS NULL OR f.deleted_at IS NULL)
                    ORDER BY m.deleted_at DESC, m.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*) FROM materials m
                    LEFT JOIN folders f ON f.id = m.folder_id
                    WHERE m.user_id = :userId
                      AND m.deleted_at IS NOT NULL
                      AND (m.folder_id IS NULL OR f.deleted_at IS NULL)
                    """,
            nativeQuery = true
    )
    Page<Material> findTrashedByUserIdOrderByDeletedAtDesc(@Param("userId") Long userId, Pageable pageable);

    @Query(
            value = """
                    SELECT m.* FROM materials m
                    LEFT JOIN folders f ON f.id = m.folder_id
                    WHERE m.user_id = :userId
                      AND m.deleted_at IS NOT NULL
                      AND (m.folder_id IS NULL OR f.deleted_at IS NULL)
                    ORDER BY m.deleted_at ASC, m.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(*) FROM materials m
                    LEFT JOIN folders f ON f.id = m.folder_id
                    WHERE m.user_id = :userId
                      AND m.deleted_at IS NOT NULL
                      AND (m.folder_id IS NULL OR f.deleted_at IS NULL)
                    """,
            nativeQuery = true
    )
    Page<Material> findTrashedByUserIdOrderByDeletedAtAsc(@Param("userId") Long userId, Pageable pageable);

    /** 요청한 자료ID 중 실제로 복구 가능한(휴지통에 있고 상위 폴더가 활성 상태인) ID만 골라낸다. */
    @Query(
            value = """
                    SELECT m.id FROM materials m
                    JOIN folders f ON f.id = m.folder_id
                    WHERE m.id IN (:materialIds)
                      AND m.user_id = :userId
                      AND m.deleted_at IS NOT NULL
                      AND f.deleted_at IS NULL
                    """,
            nativeQuery = true
    )
    List<Long> findRestorableTrashedIds(
            @Param("materialIds") List<Long> materialIds,
            @Param("userId") Long userId
    );

    @Modifying
    @Query(
            value = """
                    UPDATE materials
                    SET deleted_at = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id IN (:materialIds)
                      AND user_id = :userId
                      AND deleted_at IS NOT NULL
                    """,
            nativeQuery = true
    )
    int restoreTrashedMaterials(
            @Param("materialIds") List<Long> materialIds,
            @Param("userId") Long userId
    );

    /** 복구 결과 토스트에 "어느 폴더로 복구됐는지" 표시하기 위해, 자료가 소속된(원래) 폴더명을 함께 조회한다. */
    @Query(
            value = """
                    SELECT m.id AS materialId, f.name AS folderName
                    FROM materials m
                    JOIN folders f ON f.id = m.folder_id
                    WHERE m.id IN (:materialIds)
                      AND m.user_id = :userId
                    """,
            nativeQuery = true
    )
    List<MaterialFolderNameProjection> findFolderNamesByIds(
            @Param("materialIds") List<Long> materialIds,
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

    List<Material> findAllByFolder_Id(Long folderId);

    /** 폴더 삭제 시 그 폴더에 속한(아직 활성 상태인) 자료를 전부 휴지통 상태로 전환한다. */
    @Modifying
    @Query(
            value = """
                    UPDATE materials
                    SET deleted_at = CURRENT_TIMESTAMP,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE folder_id = :folderId
                      AND user_id = :userId
                      AND deleted_at IS NULL
                    """,
            nativeQuery = true
    )
    int trashMaterialsByFolder(
            @Param("folderId") Long folderId,
            @Param("userId") Long userId
    );

    /** 질문 문장 안에 이 사용자의 자료 제목/태그명이 부분 문자열로 언급됐는지 역방향으로 찾는다.
     *  챗봇 메타 질문("내 자료에 ~있어?")을 벡터 검색보다 먼저 구조화된 조회로 해결하기 위함. */
    @Query("""
            SELECT DISTINCT m.id
            FROM Material m
            LEFT JOIN MaterialTag mt ON mt.material = m
            LEFT JOIN mt.tag t
            WHERE m.user.id = :userId
              AND (
                  LOCATE(LOWER(t.name), LOWER(:question)) > 0
                  OR LOCATE(LOWER(m.title), LOWER(:question)) > 0
                  OR LOCATE(LOWER(m.analysisTitle), LOWER(:question)) > 0
              )
            ORDER BY m.id ASC
            """)
    List<Long> findIdsMentionedInQuestion(
            @Param("userId") Long userId,
            @Param("question") String question
    );

    long countByUser_Id(Long userId);

    Optional<Material> findFirstByUser_IdOrderByCreatedAtDesc(Long userId);

    long countByFolder_Id(Long folderId);

    @Query("SELECT m.folder.id, COUNT(m) FROM Material m WHERE m.folder.id IN :folderIds GROUP BY m.folder.id")
    List<Object[]> countGroupedByFolderIds(@Param("folderIds") List<Long> folderIds);

}


