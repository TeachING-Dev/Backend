package com.teaching.backend.domain.home.dto;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.enums.PlatformType;

import java.time.LocalDateTime;

public record HomeMaterialResponse(
        Long materialId,
        String title,
        String analysisTitle,
        String summary,
        String platformType,
        String platformImageUrl,
        Integer difficulty,
        String aiStatus,
        LocalDateTime createdAt
) {

    public static HomeMaterialResponse of(Material material, String summary) {
        PlatformType platformType = material.getPlatformType();
        AiStatus aiStatus = material.getAiStatus();

        return new HomeMaterialResponse(
                material.getId(),
                material.getTitle(),
                material.getAnalysisTitle(),
                summary,
                platformType == null ? null : platformType.name(),
                platformType == null ? null : platformType.getIconPath(),
                material.getDifficulty(),
                aiStatus == null ? null : aiStatus.name(),
                material.getCreatedAt()
        );
    }
}
