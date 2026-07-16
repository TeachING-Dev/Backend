package com.teaching.backend.domain.folder.dto.response;

import com.teaching.backend.domain.material.entity.Material;

import java.time.LocalDateTime;
import java.util.List;

public record FolderMaterialItemResponse(
        Long materialId,
        String title,
        String summary,
        String originUrl,
        List<String> tags,
        String statusAi,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static FolderMaterialItemResponse of(
            Material material,
            String summary,
            List<String> tags
    ) {
        return new FolderMaterialItemResponse(
                material.getId(),
                material.getTitle(),
                summary,
                material.getOriginalUrl(),
                tags,
                material.getAiStatus().name(),
                material.getCreatedAt(),
                material.getUpdatedAt()
        );
    }
}
