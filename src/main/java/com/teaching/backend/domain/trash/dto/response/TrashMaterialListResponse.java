package com.teaching.backend.domain.trash.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record TrashMaterialListResponse(
        List<TrashMaterialItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static TrashMaterialListResponse of(Page<TrashMaterialItemResponse> page) {
        return new TrashMaterialListResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
