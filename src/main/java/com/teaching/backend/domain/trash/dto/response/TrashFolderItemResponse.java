package com.teaching.backend.domain.trash.dto.response;

import com.teaching.backend.domain.folder.entity.Folder;

import java.time.LocalDateTime;

public record TrashFolderItemResponse(
        Long folderId,
        String name,
        LocalDateTime deletedAt
) {

    public static TrashFolderItemResponse from(Folder folder) {
        return new TrashFolderItemResponse(
                folder.getId(),
                folder.getName(),
                folder.getDeletedAt()
        );
    }
}
