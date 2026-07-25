package com.teaching.backend.domain.trash.dto.response;

import com.teaching.backend.domain.teachingmap.entity.TeachingMap;

import java.time.LocalDateTime;

public record TrashTeachingMapItemResponse(
        Long teachingMapId,
        String title,
        LocalDateTime deletedAt
) {

    public static TrashTeachingMapItemResponse from(TeachingMap teachingMap) {
        return new TrashTeachingMapItemResponse(
                teachingMap.getId(),
                teachingMap.getTitle(),
                teachingMap.getDeletedAt()
        );
    }
}
