package com.teaching.backend.domain.folder.dto.response;

import com.teaching.backend.domain.folder.entity.Folder;
import org.springframework.data.domain.Page;

import java.util.List;

public record FolderMaterialListResponse(
        Long folderId,
        String folderName,
        List<FolderMaterialItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static FolderMaterialListResponse of(
            Folder folder,
            Page<?> materialPage,
            List<FolderMaterialItemResponse> content
    ) {
        return new FolderMaterialListResponse(
                folder.getId(),
                folder.getName(),
                content,
                materialPage.getNumber(),
                materialPage.getSize(),
                materialPage.getTotalElements(),
                materialPage.getTotalPages()
        );
    }
}
