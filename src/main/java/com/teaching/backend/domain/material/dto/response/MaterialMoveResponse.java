package com.teaching.backend.domain.material.dto.response;

public record MaterialMoveResponse(
        int movedCount,
        Long currentFolderId,
        Long targetFolderId
) {

    public static MaterialMoveResponse of(int movedCount, Long currentFolderId, Long targetFolderId) {
        return new MaterialMoveResponse(movedCount, currentFolderId, targetFolderId);
    }
}
