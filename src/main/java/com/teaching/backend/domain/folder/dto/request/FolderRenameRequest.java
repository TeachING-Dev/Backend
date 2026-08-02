package com.teaching.backend.domain.folder.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record FolderRenameRequest(
        @Schema(description = "변경할 폴더명. 한글 또는 영문만 가능하며 공백, 숫자, 특수문자는 사용할 수 없습니다. 최대 10자입니다.", example = "백엔드")
        String folderName
) {

    public String normalizedFolderName() {
        return folderName.trim();
    }
}
