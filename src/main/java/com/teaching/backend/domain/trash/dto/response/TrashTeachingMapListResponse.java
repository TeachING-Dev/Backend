package com.teaching.backend.domain.trash.dto.response;

import com.teaching.backend.domain.teachingmap.entity.TeachingMap;

import java.time.LocalDateTime;

public record TrashTeachingMapListResponse(
        Long teachingMapId,
        String title,
        LocalDateTime deletedAt
) {

    public static TrashTeachingMapListResponse from(TeachingMap teachingMap) {
        return new TrashTeachingMapListResponse(
                teachingMap.getId(),
                teachingMap.getTitle(),
                teachingMap.getDeletedAt()
        );
    }
}
