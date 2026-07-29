package com.teaching.backend.domain.material.dto.response;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.enums.PlatformType;

import java.time.LocalDateTime;
import java.util.List;

public record MaterialAnalysisResponse(
        Long materialAnalysisId,
        Long materialId,
        String title,
        String originUrl,
        String platformType,
        String platformImageUrl,
        List<MaterialTagResponse> tags,
        String shortSummary,
        String fullAnalysis,
        boolean isUserEdited,
        LocalDateTime generatedAt,
        LocalDateTime updatedAt
) {

    public static MaterialAnalysisResponse of(
            MaterialAnalysis analysis,
            Material material,
            List<MaterialTagResponse> tags
    ) {
        PlatformType platformType = material.getPlatformType();

        return new MaterialAnalysisResponse(
                analysis.getId(),
                analysis.getMaterial().getId(),
                material.getTitle(),
                material.getOriginalUrl(),
                platformType == null ? null : platformType.name(),
                platformType == null ? null : platformType.getIconPath(),
                tags,
                analysis.getSummary(),
                analysis.getDetailAnalysis(),
                analysis.isUserEdited(),
                analysis.getCreatedAt(),
                analysis.getUpdatedAt()
        );
    }
}
