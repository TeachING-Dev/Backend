package com.teaching.backend.domain.folder.dto.response;

import com.teaching.backend.domain.folder.entity.Folder;

import java.time.LocalDateTime;

public record FolderDetailResponse(
        Long folderId,
        String folderName,
        Long materialCount,
        LocalDateTime updatedAt
) {

    public static FolderDetailResponse of(Folder folder, long materialCount) {
        return new FolderDetailResponse(
                folder.getId(),
                folder.getName(),
                materialCount,
                folder.getUpdatedAt()
        );
    }
}
