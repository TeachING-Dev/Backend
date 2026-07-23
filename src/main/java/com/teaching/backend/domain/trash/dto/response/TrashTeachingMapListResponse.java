package com.teaching.backend.domain.trash.dto.response;

import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.trash.util.RelativeTimeFormatter;

public record TrashTeachingMapListResponse(
        Long teachingMapId,
        String title,
        String deletedAt
) {

    public static TrashTeachingMapListResponse from(TeachingMap teachingMap) {
        return new TrashTeachingMapListResponse(
                teachingMap.getId(),
                teachingMap.getTitle(),
                RelativeTimeFormatter.format(teachingMap.getDeletedAt())
        );
    }
}
