package com.teaching.backend.domain.trash.dto.response;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.PlatformType;

import java.time.LocalDateTime;

public record TrashMaterialItemResponse(
        Long materialId,
        String title,
        String platformType,
        String platformImageUrl,
        String summary,
        String originalUrl,
        LocalDateTime createdAt,
        LocalDateTime deletedAt
) {

    public static TrashMaterialItemResponse from(Material material, String summary) {
        PlatformType platformType = material.getPlatformType();

        return new TrashMaterialItemResponse(
                material.getId(),
                material.getTitle(),
                platformType == null ? null : platformType.name(),
                platformType == null ? null : platformType.getIconPath(),
                summary,
                material.getOriginalUrl(),
                material.getCreatedAt(),
                material.getDeletedAt()
        );
    }
}
