package com.teaching.backend.domain.material.dto.response;

import com.teaching.backend.domain.material.entity.Material;

import java.time.LocalDateTime;
import java.util.List;

public record MaterialDetailResponse(
        Long materialId,
        Long folderId,
        String title,
        String originUrl,
        String summary,
        List<String> tags,
        String statusAi,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MaterialDetailResponse of(
            Material material,
            String summary,
            List<String> tags
    ) {
        return new MaterialDetailResponse(
                material.getId(),
                material.getFolder().getId(),
                material.getTitle(),
                material.getOriginalUrl(),
                summary,
                tags,
                material.getAiStatus().name(),
                material.getCreatedAt(),
                material.getUpdatedAt()
        );
    }
}
