package com.teaching.backend.domain.trash.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record TrashTeachingMapListResponse(
        List<TrashTeachingMapItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static TrashTeachingMapListResponse of(Page<TrashTeachingMapItemResponse> page) {
        return new TrashTeachingMapListResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
