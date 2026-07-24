package com.teaching.backend.domain.trash.dto.response;

import com.teaching.backend.domain.folder.entity.Folder;

import java.time.LocalDateTime;

public record TrashFolderListResponse(
        Long folderId,
        String name,
        LocalDateTime deletedAt
) {

    public static TrashFolderListResponse from(Folder folder) {
        return new TrashFolderListResponse(
                folder.getId(),
                folder.getName(),
                folder.getDeletedAt()
        );
    }
}
