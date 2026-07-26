package com.teaching.backend.domain.trash.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record TrashFolderListResponse(
        List<TrashFolderItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static TrashFolderListResponse of(Page<TrashFolderItemResponse> page) {
        return new TrashFolderListResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
