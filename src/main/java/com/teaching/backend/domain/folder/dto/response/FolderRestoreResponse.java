package com.teaching.backend.domain.folder.dto.response;

public record FolderRestoreResponse(
        Long folderId,
        Boolean isDeleted
) {

    public static FolderRestoreResponse of(
            Long folderId,
            Boolean isDeleted
    ) {
        return new FolderRestoreResponse(
                folderId,
                isDeleted
        );
    }
}
