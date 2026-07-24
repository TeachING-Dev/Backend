package com.teaching.backend.domain.teachingmap.repository;

import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
        AND (:status IS NULL OR tm.status = :status)
        AND (:type IS NULL OR tm.type = :type)
    """)
    List<TeachingMap> findAllByFilter(
            @Param("userId") Long userId,
            @Param("isDraft") boolean isDraft,
            @Param("status") TeachingMapStatus status,
            @Param("type") TeachingMapType type,
            Sort sort
    );
}
