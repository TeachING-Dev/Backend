package com.teaching.backend.domain.folder.dto.response;

import com.teaching.backend.domain.folder.entity.Folder;

public record FolderRenameResponse(
        Long folderId,
        String folderName
) {

    public static FolderRenameResponse from(Folder folder) {
        return new FolderRenameResponse(
                folder.getId(),
                folder.getName()
        );
    }
}
