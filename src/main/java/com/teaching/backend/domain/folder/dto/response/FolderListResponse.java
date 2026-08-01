package com.teaching.backend.domain.folder.dto.response;

import com.teaching.backend.domain.folder.entity.Folder;

import java.time.LocalDateTime;

public record FolderListResponse(
        Long folderId,
        String folderName,
        Long materialCount,
        LocalDateTime updatedAt
) {

    public static FolderListResponse of(Folder folder, long materialCount) {
        return new FolderListResponse(
                folder.getId(),
                folder.getName(),
                materialCount,
                folder.getUpdatedAt()
        );
    }
}