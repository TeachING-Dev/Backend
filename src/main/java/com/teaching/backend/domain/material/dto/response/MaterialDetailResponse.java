package com.teaching.backend.domain.material.dto.response;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.PlatformType;

import java.time.LocalDateTime;
import java.util.List;

public record MaterialDetailResponse(
        Long materialId,
        Long folderId,
        String title,
        String originUrl,
        String summary,
        String platformType,
        String platformImageUrl,
        List<MaterialTagResponse> tags,
        String statusAi,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MaterialDetailResponse of(
            Material material,
            String summary,
            List<MaterialTagResponse> tags
    ) {
        PlatformType platformType = material.getPlatformType();

        return new MaterialDetailResponse(
                material.getId(),
                material.getFolder().getId(),
                material.getTitle(),
                material.getOriginalUrl(),
                summary,
                platformType == null ? null : platformType.name(),
                platformType == null ? null : platformType.getIconPath(),
                tags,
                material.getAiStatus().name(),
                material.getCreatedAt(),
                material.getUpdatedAt()
        );
    }
}
