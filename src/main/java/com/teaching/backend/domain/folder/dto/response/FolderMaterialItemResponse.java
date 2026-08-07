package com.teaching.backend.domain.folder.dto.response;

import com.teaching.backend.domain.material.dto.response.MaterialTagResponse;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.PlatformType;

import java.time.LocalDateTime;
import java.util.List;

public record FolderMaterialItemResponse(
        Long materialId,
        String title,
        String summary,
        String originalUrl,
        List<MaterialTagResponse> tags,
        String platformType,
        String platformImageUrl,
        String statusAi,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static FolderMaterialItemResponse of(
            Material material,
            String summary,
            List<MaterialTagResponse> tags
    ) {
        PlatformType platformType = material.getPlatformType();

        return new FolderMaterialItemResponse(
                material.getId(),
                material.getTitle(),
                summary,
                material.getOriginalUrl(),
                tags,
                platformType == null ? null : platformType.name(),
                platformType == null ? null : platformType.getIconPath(),
                material.getAiStatus().name(),
                material.getCreatedAt(),
                material.getUpdatedAt()
        );
    }
}
