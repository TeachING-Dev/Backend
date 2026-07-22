package com.teaching.backend.domain.material.dto;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.PlatformType;

import java.time.LocalDateTime;

public record MaterialListResponse(
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

    public static MaterialListResponse of(Material material, String summary) {
        PlatformType platformType = material.getPlatformType();

        return new MaterialListResponse(
                material.getId(),
                material.getTitle(),
                material.getAnalysisTitle(),
                summary,
                platformType == null ? null : platformType.name(),
                platformType == null ? null : platformType.getIconPath(),
                material.getDifficulty(),
                material.getAiStatus().name(),
                material.getCreatedAt()
        );
    }
}
