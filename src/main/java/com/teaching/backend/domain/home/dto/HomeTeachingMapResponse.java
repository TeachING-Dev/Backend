package com.teaching.backend.domain.home.dto;

import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;

import java.time.LocalDateTime;

public record HomeTeachingMapResponse(
        Long teachingMapId,
        String title,
        String description,
        String type,
        String status,
        Integer currentSteps,
        Integer totalSteps,
        LocalDateTime createdAt
) {

    public static HomeTeachingMapResponse from(TeachingMap teachingMap) {
        TeachingMapType type = teachingMap.getType();
        TeachingMapStatus status = teachingMap.getStatus();

        return new HomeTeachingMapResponse(
                teachingMap.getId(),
                teachingMap.getTitle(),
                teachingMap.getDescription(),
                type == null ? null : type.name(),
                status == null ? null : status.name(),
                teachingMap.getCurrentSteps(),
                teachingMap.getTotalSteps(),
                teachingMap.getCreatedAt()
        );
    }
}
