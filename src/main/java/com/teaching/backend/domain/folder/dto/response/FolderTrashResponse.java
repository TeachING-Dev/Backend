package com.teaching.backend.domain.folder.dto.response;

import com.teaching.backend.domain.folder.entity.Folder;

public record FolderTrashResponse(
        Long folderId,
        Boolean isDeleted
) {

    public static FolderTrashResponse from(Folder folder) {
        return new FolderTrashResponse(
                folder.getId(),
                folder.isDeleted()
        );
    }
}
