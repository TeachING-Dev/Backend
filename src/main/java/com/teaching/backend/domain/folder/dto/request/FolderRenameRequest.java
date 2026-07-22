package com.teaching.backend.domain.folder.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record FolderRenameRequest(
        @Schema(description = "변경할 폴더명, 최대 10자", example = "Node.js")
        String folderName
) {

    public String normalizedFolderName() {
        return folderName.trim();
    }
}
