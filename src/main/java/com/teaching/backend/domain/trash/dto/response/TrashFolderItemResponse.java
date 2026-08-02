package com.teaching.backend.domain.trash.dto.response;

import com.teaching.backend.domain.folder.entity.Folder;

import java.time.LocalDateTime;

public record TrashFolderItemResponse(
        Long folderId,
        String name,
        Long materialCount,
        LocalDateTime deletedAt
) {

    public static TrashFolderItemResponse from(Folder folder) {
        return new TrashFolderItemResponse(
                folder.getId(),
                folder.getName(),
                convertToLong(folder.getItemCount()),
                folder.getDeletedAt()
        );
    }

    private static Long convertToLong(Integer itemCount) {
        return itemCount == null
                ? 0L
                : itemCount.longValue();
    }
}
