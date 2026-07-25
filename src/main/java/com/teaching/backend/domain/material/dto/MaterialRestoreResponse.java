package com.teaching.backend.domain.material.dto;

import java.util.List;

public record MaterialRestoreResponse(
        List<Long> restoredIds,
        List<Long> failedIds
) {

    public static MaterialRestoreResponse of(
            List<Long> restoredIds,
            List<Long> failedIds
    ) {
        return new MaterialRestoreResponse(restoredIds, failedIds);
    }
}
