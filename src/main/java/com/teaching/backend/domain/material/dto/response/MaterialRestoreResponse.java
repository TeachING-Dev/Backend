package com.teaching.backend.domain.material.dto.response;

public record MaterialRestoreResponse(
        int restoredCount,
        Long folderId
) {

    public static MaterialRestoreResponse of(int restoredCount, Long folderId) {
        return new MaterialRestoreResponse(restoredCount, folderId);
    }
}
