package com.teaching.backend.domain.trash.dto.response;

import com.teaching.backend.domain.folder.entity.Folder;
import org.springframework.data.domain.Page;

import java.util.List;

public record TrashFolderMaterialListResponse(
        Long folderId,
        String folderName,
        List<TrashMaterialItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static TrashFolderMaterialListResponse of(Folder folder, Page<TrashMaterialItemResponse> page) {
        return new TrashFolderMaterialListResponse(
                folder.getId(),
                folder.getName(),
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
