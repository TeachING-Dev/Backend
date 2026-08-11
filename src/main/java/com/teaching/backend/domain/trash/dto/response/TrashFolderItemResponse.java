package com.teaching.backend.domain.trash.dto.response;

import com.teaching.backend.domain.folder.entity.Folder;

import java.time.LocalDateTime;

public record TrashFolderItemResponse(
        Long folderId,
        String name,
        Long materialCount,
        LocalDateTime deletedAt
) {

    public static TrashFolderItemResponse from(Folder folder, long materialCount) {
        return new TrashFolderItemResponse(
                folder.getId(),
                folder.getName(),
                materialCount,
                folder.getDeletedAt()
        );
    }
}
