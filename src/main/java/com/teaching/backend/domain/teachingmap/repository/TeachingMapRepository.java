package com.teaching.backend.domain.teachingmap.repository;

import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeachingMapRepository extends JpaRepository<TeachingMap, Long> {

    List<TeachingMap> findAllByUser_IdAndIsDraftFalseAndDeletedAtIsNull(
            Long userId,
            Sort sort
    );

    List<TeachingMap> findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
            Long userId,
            TeachingMapStatus status,
            Sort sort
    );

    Page<TeachingMap> findAllByUser_IdAndIsDraftFalseAndDeletedAtIsNull(
            Long userId,
            Pageable pageable
    );

    Page<TeachingMap> findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
            Long userId,
            TeachingMapStatus status,
            Pageable pageable
    );

    @Query("""
        SELECT tm FROM TeachingMap tm
        WHERE tm.user.id = :userId
        AND tm.isDraft = :isDraft
        AND tm.deletedAt IS NULL
        AND (:status IS NULL OR tm.status = :status)
        AND (:type IS NULL OR tm.type = :type)
    """)
    Page<TeachingMap> findAllByFilter(
            @Param("userId") Long userId,
            @Param("isDraft") boolean isDraft,
            @Param("status") TeachingMapStatus status,
            @Param("type") TeachingMapType type,
            Pageable pageable
    );

    @Query(
            value = "SELECT * FROM teaching_maps WHERE user_id = :userId AND deleted_at IS NOT NULL ORDER BY deleted_at DESC, id DESC",
            countQuery = "SELECT COUNT(*) FROM teaching_maps WHERE user_id = :userId AND deleted_at IS NOT NULL",
            nativeQuery = true
    )
    Page<TeachingMap> findTrashedByUserIdOrderByDeletedAtDesc(@Param("userId") Long userId, Pageable pageable);

    @Query(
            value = "SELECT * FROM teaching_maps WHERE user_id = :userId AND deleted_at IS NOT NULL ORDER BY deleted_at ASC, id ASC",
            countQuery = "SELECT COUNT(*) FROM teaching_maps WHERE user_id = :userId AND deleted_at IS NOT NULL",
            nativeQuery = true
    )
    Page<TeachingMap> findTrashedByUserIdOrderByDeletedAtAsc(@Param("userId") Long userId, Pageable pageable);

    /** 요청한 티칭맵ID 중 실제로 복구 가능한(휴지통에 있는) ID만 골라낸다. */
    @Query(
            value = """
                    SELECT id FROM teaching_maps
                    WHERE id IN (:teachingMapIds)
                      AND user_id = :userId
                      AND deleted_at IS NOT NULL
                    """,
            nativeQuery = true
    )
    List<Long> findRestorableTrashedIds(
            @Param("teachingMapIds") List<Long> teachingMapIds,
            @Param("userId") Long userId
    );

    @Modifying
    @Query(
            value = """
                    UPDATE teaching_maps
                    SET deleted_at = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id IN (:teachingMapIds)
                      AND user_id = :userId
                      AND deleted_at IS NOT NULL
                    """,
            nativeQuery = true
    )
    int restoreTrashedTeachingMaps(
            @Param("teachingMapIds") List<Long> teachingMapIds,
            @Param("userId") Long userId
    );

    Optional<TeachingMap> findByIdAndUser_Id(Long id, Long userId);
    Optional<TeachingMap> findByIdAndUser_IdAndDeletedAtIsNull(Long id, Long userId);
    List<TeachingMap> findAllByIdInAndUser_IdAndDeletedAtIsNull(List<Long> ids, Long userId);

    List<TeachingMap> findAllByStatusAndIsDraftFalseAndDeletedAtIsNullAndCreatedAtLessThanEqual(
            TeachingMapStatus status,
            java.time.LocalDateTime createdAt
    );

}
