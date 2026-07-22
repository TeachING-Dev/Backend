package com.teaching.backend.domain.material.dto.response;

public record MaterialTrashResponse(
        int deletedCount,
        Long folderId
) {

    public static MaterialTrashResponse of(int deletedCount, Long folderId) {
        return new MaterialTrashResponse(deletedCount, folderId);
    }
}
