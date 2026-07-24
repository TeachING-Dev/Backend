package com.teaching.backend.domain.material.dto.response;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.MaterialAnalyzeResultType;
import com.teaching.backend.domain.material.enums.PlatformType;

public record MaterialAnalyzeResponse(
        MaterialAnalyzeResultType resultType,
        Long existingMaterialId,
        String title,
        String originalUrl,
        String platformType,
        String status
) {

    public static MaterialAnalyzeResponse alreadyAnalyzed(Material material) {
        PlatformType platformType = material.getPlatformType();

        return new MaterialAnalyzeResponse(
                MaterialAnalyzeResultType.ALREADY_ANALYZED,
                material.getId(),
                material.getTitle(),
                material.getOriginalUrl(),
                platformType == null ? null : platformType.name(),
                material.getAiStatus() == null ? null : material.getAiStatus().name()
        );
    }

    public static MaterialAnalyzeResponse analysisRequired(
            String originalUrl,
            PlatformType platformType
    ) {
        return new MaterialAnalyzeResponse(
                MaterialAnalyzeResultType.ANALYSIS_REQUIRED,
                null,
                null,
                originalUrl,
                platformType == null ? null : platformType.name(),
                null
        );
    }
}
