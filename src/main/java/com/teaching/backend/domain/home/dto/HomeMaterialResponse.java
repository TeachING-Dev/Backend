package com.teaching.backend.domain.home.dto;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.enums.PlatformType;

import java.time.LocalDateTime;

public record HomeMaterialResponse(
        Long materialId,
        Long folderId,
        String title,
        String platformType,
        String platformImageUrl,
        String aiStatus,
        LocalDateTime createdAt
) {

    public static HomeMaterialResponse from(Material material) {
        PlatformType platformType = material.getPlatformType();
        AiStatus aiStatus = material.getAiStatus();

        return new HomeMaterialResponse(
                material.getId(),
                material.getFolderId(),
                material.getTitle(),
                platformType == null ? null : platformType.name(),
                platformType == null ? null : platformType.getIconPath(),
                aiStatus == null ? null : aiStatus.name(),
                material.getCreatedAt()
        );
    }
}
