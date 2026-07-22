package com.teaching.backend.domain.teachingmap.dto;

import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;

import java.time.LocalDateTime;

public record TeachingMapListResponse(
        Long teachingMapId,
        String title,
        String description,
        String type,
        String status,
        Integer currentSteps,
        Integer totalSteps,
        LocalDateTime createdAt
) {

    public static TeachingMapListResponse from(TeachingMap teachingMap) {
        TeachingMapType type = teachingMap.getType();
        TeachingMapStatus status = teachingMap.getStatus();

        return new TeachingMapListResponse(
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
