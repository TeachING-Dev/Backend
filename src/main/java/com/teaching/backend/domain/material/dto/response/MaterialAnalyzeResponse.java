package com.teaching.backend.domain.material.dto.response;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisPipelineResult;
import com.teaching.backend.domain.material.enums.MaterialAnalyzeResultType;
import com.teaching.backend.domain.material.enums.PlatformType;

public record MaterialAnalyzeResponse(
        MaterialAnalyzeResultType resultType,
        Long existingMaterialId,
        Long materialId,
        Long materialAnalysisId,
        String title,
        String originalUrl,
        String platformType,
        String status,
        Integer chunkCount
) {

    public static MaterialAnalyzeResponse alreadyAnalyzed(
            Material material,
            Long materialAnalysisId,
            Integer chunkCount
    ) {
        PlatformType platformType = material.getPlatformType();

        return new MaterialAnalyzeResponse(
                MaterialAnalyzeResultType.ALREADY_ANALYZED,
                material.getId(),
                null,
                materialAnalysisId,
                material.getTitle(),
                material.getOriginalUrl(),
                platformType == null ? null : platformType.name(),
                material.getAiStatus() == null ? null : material.getAiStatus().name(),
                chunkCount
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
                null,
                null,
                originalUrl,
                platformType == null ? null : platformType.name(),
                null,
                null
        );
    }

    public static MaterialAnalyzeResponse completed(MaterialAiAnalysisPipelineResult result) {
        PlatformType platformType = result.platformType();

        return new MaterialAnalyzeResponse(
                MaterialAnalyzeResultType.ANALYSIS_COMPLETED,
                null,
                result.materialId(),
                result.materialAnalysisId(),
                result.extractedContent() == null ? null : result.extractedContent().title(),
                result.originalUrl(),
                platformType == null ? null : platformType.name(),
                "COMPLETED",
                result.chunkCount()
        );
    }
}
