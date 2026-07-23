package com.teaching.backend.domain.trash.dto.response;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.trash.util.RelativeTimeFormatter;

public record TrashFolderListResponse(
        Long folderId,
        String name,
        String deletedAt
) {

    public static TrashFolderListResponse from(Folder folder) {
        return new TrashFolderListResponse(
                folder.getId(),
                folder.getName(),
                RelativeTimeFormatter.format(folder.getDeletedAt())
        );
    }
}
