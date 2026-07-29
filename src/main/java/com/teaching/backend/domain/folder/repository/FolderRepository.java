package com.teaching.backend.domain.folder.repository;

import com.teaching.backend.domain.folder.entity.Folder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    List<Folder> findAllByUser_Id(
            Long userId,
            Sort sort
    );

    long countByUser_Id(Long userId);

    @Query("""
            SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END
            FROM Folder f
            WHERE f.user.id = :userId
              AND f.name = :name
              AND f.deletedAt IS NULL
            """)
    boolean existsActiveByUserIdAndName(
            @Param("userId") Long userId,
            @Param("name") String name
    );

    Optional<Folder> findByIdAndUser_Id(
            Long folderId,
            Long userId
    );

    Optional<Folder> findByUser_IdAndName(
            Long userId,
            String name
    );

    Optional<Folder> findByUser_IdAndNameAndDeletedAtIsNull(
            Long userId,
            String name
    );

    @Query(
            value = "SELECT * FROM folders WHERE user_id = :userId AND deleted_at IS NOT NULL ORDER BY deleted_at DESC, id DESC",
            countQuery = "SELECT COUNT(*) FROM folders WHERE user_id = :userId AND deleted_at IS NOT NULL",
            nativeQuery = true
    )
    Page<Folder> findTrashedByUserIdOrderByDeletedAtDesc(@Param("userId") Long userId, Pageable pageable);

    @Query(
            value = "SELECT * FROM folders WHERE user_id = :userId AND deleted_at IS NOT NULL ORDER BY deleted_at ASC, id ASC",
            countQuery = "SELECT COUNT(*) FROM folders WHERE user_id = :userId AND deleted_at IS NOT NULL",
            nativeQuery = true
    )
    Page<Folder> findTrashedByUserIdOrderByDeletedAtAsc(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END
            FROM Folder f
            WHERE f.user.id = :userId
              AND f.name = :name
              AND f.id <> :folderId
              AND f.deletedAt IS NULL
            """)
    boolean existsActiveByUserIdAndNameAndIdNot(
            @Param("userId") Long userId,
            @Param("name") String name,
            @Param("folderId") Long folderId
    );

    @Query(
            value = "SELECT COUNT(*) FROM folders WHERE id = :folderId",
            nativeQuery = true
    )
    long countByIdIncludingDeleted(@Param("folderId") Long folderId);

    @Query(
            value = "SELECT COUNT(*) FROM folders WHERE id = :folderId AND user_id = :userId",
            nativeQuery = true
    )
    long countByIdAndUserIdIncludingDeleted(
            @Param("folderId") Long folderId,
            @Param("userId") Long userId
    );

    @Query(
            value = "SELECT COUNT(*) FROM folders WHERE id = :folderId AND user_id = :userId AND deleted_at IS NOT NULL",
            nativeQuery = true
    )
    long countDeletedByIdAndUserId(
            @Param("folderId") Long folderId,
            @Param("userId") Long userId
    );

    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM folders target
                    JOIN folders active
                      ON active.user_id = target.user_id
                     AND active.name = target.name
                     AND active.id <> target.id
                     AND active.deleted_at IS NULL
                    WHERE target.id = :folderId
                      AND target.user_id = :userId
                    """,
            nativeQuery = true
    )
    long countActiveNameConflictForRestore(
            @Param("folderId") Long folderId,
            @Param("userId") Long userId
    );

    @Modifying
    @Query(
            value = "UPDATE folders SET deleted_at = NULL WHERE id = :folderId AND user_id = :userId AND deleted_at IS NOT NULL",
            nativeQuery = true
    )
    int restoreDeletedFolder(
            @Param("folderId") Long folderId,
            @Param("userId") Long userId
    );

    /** 요청한 폴더ID 중 실제로 복구 가능한(휴지통에 있고, 활성 폴더와 이름이 겹치지 않는) ID만 골라낸다. */
    @Query(
            value = """
                    SELECT target.id FROM folders target
                    WHERE target.id IN (:folderIds)
                      AND target.user_id = :userId
                      AND target.deleted_at IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1 FROM folders active
                          WHERE active.user_id = target.user_id
                            AND active.name = target.name
                            AND active.id <> target.id
                            AND active.deleted_at IS NULL
                      )
                    """,
            nativeQuery = true
    )
    List<Long> findRestorableTrashedIds(
            @Param("folderIds") List<Long> folderIds,
            @Param("userId") Long userId
    );

    @Modifying
    @Query(
            value = """
                    UPDATE folders
                    SET deleted_at = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id IN (:folderIds)
                      AND user_id = :userId
                      AND deleted_at IS NOT NULL
                    """,
            nativeQuery = true
    )
    int restoreTrashedFolders(
            @Param("folderIds") List<Long> folderIds,
            @Param("userId") Long userId
    );
}
