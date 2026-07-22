package com.teaching.backend.domain.teachingmap.repository;

import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeachingMapRepository extends JpaRepository<TeachingMap, Long> {

    Page<TeachingMap> findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
            Long userId,
            TeachingMapStatus status,
            Pageable pageable
    );
}
