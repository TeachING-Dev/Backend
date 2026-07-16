package com.teaching.backend.domain.folder.dto.response;

import com.teaching.backend.domain.folder.entity.Folder;

import java.time.LocalDateTime;

public record FolderListResponse(
        Long folderId,
        String folderName,
        Long materialCount,
        LocalDateTime updatedAt
) {

    public static FolderListResponse from(Folder folder) {
        return new FolderListResponse(
                folder.getId(),
                folder.getName(),
                convertToLong(folder.getItemCount()),
                folder.getUpdatedAt()
        );
    }

    private static Long convertToLong(Integer itemCount) {
        return itemCount == null
                ? 0L
                : itemCount.longValue();
    }
}