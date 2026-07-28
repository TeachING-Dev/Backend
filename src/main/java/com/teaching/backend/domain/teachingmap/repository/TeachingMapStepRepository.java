package com.teaching.backend.domain.teachingmap.repository;

import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.entity.TeachingMapStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


public interface TeachingMapStepRepository extends JpaRepository<TeachingMapStep, Long> {

        @Query("""
        SELECT DISTINCT s.material.platformType
        FROM TeachingMapStep s
        WHERE s.teachingMap.id = :teachingMapId
    """)
        List<PlatformType> findDistinctPlatformTypesByTeachingMapId(@Param("teachingMapId") Long teachingMapId);

        @Query("""
        SELECT DISTINCT s.teachingMap.id AS teachingMapId, s.material.platformType AS platformType
        FROM TeachingMapStep s
        WHERE s.teachingMap.id IN :teachingMapIds
    """)
        List<TeachingMapPlatformProjection> findDistinctPlatformTypesByTeachingMapIdIn(
                @Param("teachingMapIds") List<Long> teachingMapIds
        );

        Optional<TeachingMapStep> findByIdAndTeachingMapId(Long id, Long teachingMapId);

        List<TeachingMapStep> findByTeachingMapIdAndDeletedAtIsNullOrderByStepOrder(Long teachingMapId);
    }


