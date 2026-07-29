package com.teaching.backend.domain.folder.dto.response;

import java.util.List;

public record FolderTrashRestoreResponse(
        List<Long> restoredIds,
        List<Long> failedIds
) {

    public static FolderTrashRestoreResponse of(
            List<Long> restoredIds,
            List<Long> failedIds
    ) {
        return new FolderTrashRestoreResponse(restoredIds, failedIds);
    }
}
