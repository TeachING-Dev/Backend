package com.teaching.backend.domain.teachingmap.repository;

import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeachingMapRepository extends JpaRepository<TeachingMap, Long> {

    @Query(
            value = "SELECT * FROM teaching_maps WHERE user_id = :userId AND deleted_at IS NOT NULL ORDER BY deleted_at DESC",
            nativeQuery = true
    )
    List<TeachingMap> findTrashedByUserIdOrderByDeletedAtDesc(@Param("userId") Long userId);

    @Query(
            value = "SELECT * FROM teaching_maps WHERE user_id = :userId AND deleted_at IS NOT NULL ORDER BY deleted_at ASC",
            nativeQuery = true
    )
    List<TeachingMap> findTrashedByUserIdOrderByDeletedAtAsc(@Param("userId") Long userId);

    @Query(
            value = "SELECT COUNT(*) FROM teaching_maps WHERE id = :teachingMapId AND user_id = :userId",
            nativeQuery = true
    )
    long countByIdAndUserIdIncludingDeleted(
            @Param("teachingMapId") Long teachingMapId,
            @Param("userId") Long userId
    );

    @Query(
            value = "SELECT COUNT(*) FROM teaching_maps WHERE id = :teachingMapId AND user_id = :userId AND deleted_at IS NOT NULL",
            nativeQuery = true
    )
    long countDeletedByIdAndUserId(
            @Param("teachingMapId") Long teachingMapId,
            @Param("userId") Long userId
    );

    @Modifying
    @Query(
            value = "UPDATE teaching_maps SET deleted_at = NULL WHERE id = :teachingMapId AND user_id = :userId AND deleted_at IS NOT NULL",
            nativeQuery = true
    )
    int restoreDeletedTeachingMap(
            @Param("teachingMapId") Long teachingMapId,
            @Param("userId") Long userId
    );
}
