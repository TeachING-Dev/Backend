package com.teaching.backend.domain.folder.repository;

import com.teaching.backend.domain.folder.entity.Folder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /** 폴더 삭제(trash)와 그 폴더로의 자료 배치(finalize/move)가 동시에 일어나 활성 자료가
     *  이미 삭제된 폴더에 남는 경쟁 조건을 막기 위해, 두 흐름이 같은 폴더 행을 잠그고 순서대로 처리하게 한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Folder f WHERE f.id = :folderId AND f.user.id = :userId")
    Optional<Folder> findByIdAndUser_IdForUpdate(
            @Param("folderId") Long folderId,
            @Param("userId") Long userId
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

    /**
     * 휴지통에 있는 폴더 1개를, 활성 폴더와 이름이 겹치지 않는 경우에만 복구한다.
     * "복구 가능 여부 확인"과 "복구 UPDATE"를 하나의 원자적 쿼리로 묶어, 확인과 실행 사이에
     * 다른 요청이 끼어들어(예: 동시에 폴더명 변경) 이름이 겹치게 되는 경쟁 조건을 원천 차단한다.
     * MySQL은 UPDATE 대상 테이블을 서브쿼리에서 직접 참조하지 못하게 막기 때문에, 대상 ID를
     * 고르는 조회를 파생 테이블로 한 번 더 감싸(JOIN) 우회한다.
     */
    @Modifying
    @Query(
            value = """
                    UPDATE folders target
                    JOIN (
                        SELECT * FROM (
                            SELECT f.id FROM folders f
                            WHERE f.id = :folderId
                              AND f.user_id = :userId
                              AND f.deleted_at IS NOT NULL
                              AND NOT EXISTS (
                                  SELECT 1 FROM folders active
                                  WHERE active.user_id = f.user_id
                                    AND active.name = f.name
                                    AND active.id <> f.id
                                    AND active.deleted_at IS NULL
                              )
                        ) AS restorable
                    ) AS restorable ON restorable.id = target.id
                    SET target.deleted_at = NULL,
                        target.updated_at = CURRENT_TIMESTAMP
                    """,
            nativeQuery = true
    )
    int restoreTrashedFolderIfNameAvailable(
            @Param("folderId") Long folderId,
            @Param("userId") Long userId
    );
}
