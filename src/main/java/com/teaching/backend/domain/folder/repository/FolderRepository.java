package com.teaching.backend.domain.folder.repository;

import com.teaching.backend.domain.folder.entity.Folder;
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

    boolean existsByUser_IdAndName(
            Long userId,
            String name
    );

    Optional<Folder> findByIdAndUser_Id(
            Long folderId,
            Long userId
    );

    boolean existsByUser_IdAndNameAndIdNot(
            Long userId,
            String name,
            Long folderId
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
}
