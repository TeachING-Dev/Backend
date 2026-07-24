package com.teaching.backend.domain.teachingmap.dto.response;

public record TeachingMapRestoreResponse(
        Long teachingMapId,
        Boolean isDeleted
) {

    public static TeachingMapRestoreResponse of(
            Long teachingMapId,
            Boolean isDeleted
    ) {
        return new TeachingMapRestoreResponse(teachingMapId, isDeleted);
    }
}
