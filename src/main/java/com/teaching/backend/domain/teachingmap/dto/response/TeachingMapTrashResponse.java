package com.teaching.backend.domain.teachingmap.dto.response;

import java.util.List;

public record TeachingMapTrashResponse(
        List<Long> deletedTeachingMapIds,
        Integer deletedCount
) {
    public static TeachingMapTrashResponse of(List<Long> deletedTeachingMapIds) {
        return new TeachingMapTrashResponse(deletedTeachingMapIds, deletedTeachingMapIds.size());
    }
}