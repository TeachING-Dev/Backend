package com.teaching.backend.domain.teachingmap.repository;

import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
