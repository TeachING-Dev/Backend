package com.teaching.backend.domain.folder.dto.response;

import com.teaching.backend.domain.folder.entity.Folder;

public record FolderCreateResponse(
        Long folderId,
        String folderName
) {

    public static FolderCreateResponse from(Folder folder) {
        return new FolderCreateResponse(
                folder.getId(),
                folder.getName()
        );
    }
}