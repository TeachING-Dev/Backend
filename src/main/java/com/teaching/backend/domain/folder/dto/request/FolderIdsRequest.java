package com.teaching.backend.domain.folder.dto.request;

import java.util.List;

public record FolderIdsRequest(
        List<Long> folderIds
) {
}
