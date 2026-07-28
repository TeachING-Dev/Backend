package com.teaching.backend.domain.material.dto.response;

import java.util.List;

public record MaterialFinalizeResponse(
        Long materialId,
        Long folderId,
        List<Long> tagIds
) {

    public MaterialFinalizeResponse {
        tagIds = tagIds == null ? List.of() : List.copyOf(tagIds);
    }
}
