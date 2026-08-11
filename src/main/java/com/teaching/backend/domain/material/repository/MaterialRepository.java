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

import java.time.LocalDateTime;
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

    /**
     * 자료 자신이 휴지통 상태이면서, "폴더와 함께" 휴지통에 들어간 게 아닌 것만 노출한다
     * (그런 자료는 폴더 휴지통 상세 목록에만 나타나야 하므로 여기서 제외한다).
     * 폴더가 없거나, 폴더가 활성 상태이거나, 폴더는 나중에 삭제됐지만 이 자료는 그 전에
     * 이미 개별적으로 삭제됐던 경우(m.deleted_at < f.deleted_at)는 폴더 삭제와 무관한
     * 독립적인 휴지통 항목이므로 그대로 노출한다.
     */
    @Query(
            value = """
                    SELECT m.* FROM materials m
                    LEFT JOIN folders f ON f.id = m.folder_id
                    WHERE m.user_id = :userId
                      AND m.deleted_at IS NOT NULL
                      AND (m.folder_id IS NULL OR f.deleted_at IS NULL OR m.deleted_at < f.deleted_at)
                    ORDER BY m.deleted_at DESC, m.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*) FROM materials m
                    LEFT JOIN folders f ON f.id = m.folder_id
                    WHERE m.user_id = :userId
                      AND m.deleted_at IS NOT NULL
                      AND (m.folder_id IS NULL OR f.deleted_at IS NULL OR m.deleted_at < f.deleted_at)
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
                      AND (m.folder_id IS NULL OR f.deleted_at IS NULL OR m.deleted_at < f.deleted_at)
                    ORDER BY m.deleted_at ASC, m.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(*) FROM materials m
                    LEFT JOIN folders f ON f.id = m.folder_id
                    WHERE m.user_id = :userId
                      AND m.deleted_at IS NOT NULL
                      AND (m.folder_id IS NULL OR f.deleted_at IS NULL OR m.deleted_at < f.deleted_at)
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

    /**
     * 폴더 삭제 시 그 폴더에 속한(아직 활성 상태인) 자료를 전부 휴지통 상태로 전환한다.
     * deletedAt은 DB의 CURRENT_TIMESTAMP가 아니라 폴더 자신의 삭제 시각(folder.getDeletedAt())을
     * 그대로 넘겨받아 써야 한다 — 그래야 폴더와 "같이" 삭제된 자료만 폴더 복구 시 함께 복구되고,
     * 폴더 삭제 이전에 이미 개별적으로 휴지통에 있던 자료는 (deleted_at < 폴더 deletedAt이므로)
     * restoreTrashedMaterialsByFolder / countDeletedByFolderIdAndUserId 에서 자동으로 제외된다.
     */
    @Modifying
    @Query(
            value = """
                    UPDATE materials
                    SET deleted_at = :deletedAt,
                        updated_at = :deletedAt
                    WHERE folder_id = :folderId
                      AND user_id = :userId
                      AND deleted_at IS NULL
                    """,
            nativeQuery = true
    )
    int trashMaterialsByFolder(
            @Param("folderId") Long folderId,
            @Param("userId") Long userId,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    /**
     * 폴더 복구 시 그 폴더 안에서 함께 휴지통으로 이동했던 자료를 폴더와 함께 복구한다.
     * folderDeletedAt(폴더가 삭제됐던 시각)보다 먼저 삭제된(=폴더와 무관하게 개별적으로 먼저
     * 휴지통에 있던) 자료는 제외한다 — 그 자료들은 폴더 삭제와 상관없이 사용자가 별도로 지운
     * 것이라, 폴더를 복구한다고 같이 되살아나면 안 된다.
     *
     * folders 테이블을 라이브로 조인하지 않고 folderDeletedAt을 호출자가 직접 넘기는 이유:
     * 호출하는 쪽(FolderService.restoreFolder, TrashService.restoreFolders)에서 이 메서드보다
     * "폴더 자신의 deleted_at을 NULL로 되돌리는 복구"가 먼저 실행되면, 라이브 조인으로는
     * f.deleted_at이 이미 NULL이라 어떤 자료도 매칭되지 않는다 — 호출 순서에 안전하도록
     * 폴더가 삭제됐던 시각의 스냅샷을 호출 전에 미리 받아서 파라미터로 고정한다.
     */
    @Modifying
    @Query(
            value = """
                    UPDATE materials
                    SET deleted_at = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE folder_id = :folderId
                      AND user_id = :userId
                      AND deleted_at IS NOT NULL
                      AND deleted_at >= :folderDeletedAt
                    """,
            nativeQuery = true
    )
    int restoreTrashedMaterialsByFolder(
            @Param("folderId") Long folderId,
            @Param("userId") Long userId,
            @Param("folderDeletedAt") LocalDateTime folderDeletedAt
    );

    /** restoreTrashedMaterialsByFolder와 동일한 기준(폴더와 함께 삭제된 자료만)으로 복구 대상 개수를 센다. */
    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM materials
                    WHERE folder_id = :folderId
                      AND user_id = :userId
                      AND deleted_at IS NOT NULL
                      AND deleted_at >= :folderDeletedAt
                    """,
            nativeQuery = true
    )
    long countDeletedByFolderIdAndUserId(
            @Param("folderId") Long folderId,
            @Param("userId") Long userId,
            @Param("folderDeletedAt") LocalDateTime folderDeletedAt
    );

    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM materials
                    WHERE id IN (:materialIds)
                      AND user_id = :userId
                      AND deleted_at IS NOT NULL
                    """,
            nativeQuery = true
    )
    long countDeletedByMaterialIdsAndUserId(
            @Param("materialIds") List<Long> materialIds,
            @Param("userId") Long userId
    );

    @Query(
            value = """
                    SELECT folder_id AS folderId, COUNT(*) AS materialCount
                    FROM materials
                    WHERE id IN (:materialIds)
                      AND user_id = :userId
                      AND deleted_at IS NOT NULL
                      AND folder_id IS NOT NULL
                    GROUP BY folder_id
                    """,
            nativeQuery = true
    )
    List<FolderMaterialRestoreCountProjection> countDeletedByFolderIdForRestore(
            @Param("materialIds") List<Long> materialIds,
            @Param("userId") Long userId
    );

    /**
     * 휴지통 폴더 목록 조회용: 폴더와 "함께" 삭제된(휴지통으로 같이 이동한) 자료 개수를 폴더별로 집계한다.
     * findTrashedByFolderIdOrderByDeletedAtDesc/Asc, restoreTrashedMaterialsByFolder 와 동일한 기준
     * (m.deleted_at >= f.deleted_at)을 써야, 폴더 삭제 이전에 개별적으로 먼저 삭제됐던 자료가
     * 이 개수에 섞여 들어가지 않는다.
     */
    @Query(
            value = """
                    SELECT m.folder_id AS folderId, COUNT(*) AS materialCount
                    FROM materials m
                    JOIN folders f ON f.id = m.folder_id
                    WHERE m.folder_id IN (:folderIds)
                      AND m.user_id = :userId
                      AND m.deleted_at IS NOT NULL
                      AND m.deleted_at >= f.deleted_at
                    GROUP BY m.folder_id
                    """,
            nativeQuery = true
    )
    List<FolderMaterialRestoreCountProjection> countDeletedByFolderIdsAndUserId(
            @Param("folderIds") List<Long> folderIds,
            @Param("userId") Long userId
    );

    /**
     * 휴지통에 있는 폴더 상세 조회용: 그 폴더 안에 있던(폴더와 "함께" 휴지통으로 이동한) 자료를
     * 최신순으로 조회한다. 폴더 삭제 이전에 이미 개별적으로 삭제됐던 자료(m.deleted_at < f.deleted_at)는
     * 이 폴더와 무관한 독립적인 휴지통 항목이므로 제외한다 — countDeletedByFolderIdAndUserId /
     * restoreTrashedMaterialsByFolder 와 동일한 기준을 사용해야 폴더 카드의 materialCount와
     * 상세 목록에 보이는 개수가 일치한다.
     */
    @Query(
            value = """
                    SELECT m.* FROM materials m
                    JOIN folders f ON f.id = m.folder_id
                    WHERE m.folder_id = :folderId
                      AND m.user_id = :userId
                      AND m.deleted_at IS NOT NULL
                      AND m.deleted_at >= f.deleted_at
                    ORDER BY m.deleted_at DESC, m.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*) FROM materials m
                    JOIN folders f ON f.id = m.folder_id
                    WHERE m.folder_id = :folderId
                      AND m.user_id = :userId
                      AND m.deleted_at IS NOT NULL
                      AND m.deleted_at >= f.deleted_at
                    """,
            nativeQuery = true
    )
    Page<Material> findTrashedByFolderIdOrderByDeletedAtDesc(
            @Param("folderId") Long folderId,
            @Param("userId") Long userId,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT m.* FROM materials m
                    JOIN folders f ON f.id = m.folder_id
                    WHERE m.folder_id = :folderId
                      AND m.user_id = :userId
                      AND m.deleted_at IS NOT NULL
                      AND m.deleted_at >= f.deleted_at
                    ORDER BY m.deleted_at ASC, m.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(*) FROM materials m
                    JOIN folders f ON f.id = m.folder_id
                    WHERE m.folder_id = :folderId
                      AND m.user_id = :userId
                      AND m.deleted_at IS NOT NULL
                      AND m.deleted_at >= f.deleted_at
                    """,
            nativeQuery = true
    )
    Page<Material> findTrashedByFolderIdOrderByDeletedAtAsc(
            @Param("folderId") Long folderId,
            @Param("userId") Long userId,
            Pageable pageable
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


