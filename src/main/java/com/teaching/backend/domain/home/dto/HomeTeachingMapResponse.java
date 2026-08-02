package com.teaching.backend.domain.home.dto;

import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;
import com.teaching.backend.domain.teachingmap.dto.response.SourcePlatform;

import java.time.LocalDateTime;
import java.util.List;

public record HomeTeachingMapResponse(
        Long teachingMapId,
        String title,
        String description,
        String type,
        String status,
        List<SourcePlatform> sourcePlatforms,
        LocalDateTime createdAt
) {

    public static HomeTeachingMapResponse from(
            TeachingMap teachingMap,
            List<SourcePlatform> sourcePlatforms
    ) {
        TeachingMapType type = teachingMap.getType();
        TeachingMapStatus status = teachingMap.getStatus();

        return new HomeTeachingMapResponse(
                teachingMap.getId(),
                teachingMap.getTitle(),
                teachingMap.getDescription(),
                type == null ? null : type.name(),
                status == null ? null : status.name(),
                sourcePlatforms == null ? List.of() : sourcePlatforms,
                teachingMap.getCreatedAt()
        );
    }
}
