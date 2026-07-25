package com.teaching.backend.domain.teachingmap.dto.response;

import java.util.List;

public record TeachingMapRestoreResponse(
        List<Long> restoredIds,
        List<Long> failedIds
) {

    public static TeachingMapRestoreResponse of(
            List<Long> restoredIds,
            List<Long> failedIds
    ) {
        return new TeachingMapRestoreResponse(restoredIds, failedIds);
    }
}
