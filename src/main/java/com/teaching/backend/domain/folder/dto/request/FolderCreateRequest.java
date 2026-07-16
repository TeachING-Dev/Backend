package com.teaching.backend.domain.folder.dto.request;

public record FolderCreateRequest(
        String folderName
) {

    public String normalizedFolderName() {
        return folderName.trim();
    }
}
