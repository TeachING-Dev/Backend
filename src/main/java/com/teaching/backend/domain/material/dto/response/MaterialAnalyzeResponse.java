package com.teaching.backend.domain.material.dto.response;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisPipelineResult;
import com.teaching.backend.domain.material.enums.MaterialAnalyzeResultType;
import com.teaching.backend.domain.material.enums.PlatformType;

public record MaterialAnalyzeResponse(
        Long materialAnalysisId,
        MaterialAnalyzeResultType resultType,
        Long materialId,
        Long existingMaterialId,
        String originalUrl,
        String title,
        String platformType,
        String status,
        Integer chunkCount,
        Long recommendedFolderId,
        String recommendedFolderName
) {

    public static MaterialAnalyzeResponse alreadyAnalyzed(
            Material material,
            Long materialAnalysisId,
            Integer chunkCount
    ) {
        PlatformType platformType = material.getPlatformType();

        return new MaterialAnalyzeResponse(
                materialAnalysisId,
                MaterialAnalyzeResultType.ALREADY_ANALYZED,
                material.getId(),
                material.getId(),
                material.getOriginalUrl(),
                material.getTitle(),
                platformType == null ? null : platformType.name(),
                material.getAiStatus() == null ? null : material.getAiStatus().name(),
                chunkCount,
                material.getFolderId(),
                material.getFolder() == null ? null : material.getFolder().getName()
        );
    }

    public static MaterialAnalyzeResponse analysisRequired(
            String originalUrl,
            PlatformType platformType
    ) {
        return new MaterialAnalyzeResponse(
                null,
                MaterialAnalyzeResultType.ANALYSIS_REQUIRED,
                null,
                null,
                originalUrl,
                null,
                platformType == null ? null : platformType.name(),
                null,
                null,
                null,
                null
        );
    }

    public static MaterialAnalyzeResponse completed(MaterialAiAnalysisPipelineResult result) {
        PlatformType platformType = result.platformType();
        String title = result.extractedContent() == null ? null : result.extractedContent().title();

        return new MaterialAnalyzeResponse(
                result.materialAnalysisId(),
                MaterialAnalyzeResultType.ANALYSIS_COMPLETED,
                result.materialId(),
                null,
                result.originalUrl(),
                title,
                platformType == null ? null : platformType.name(),
                "COMPLETED",
                result.chunkCount(),
                result.recommendedFolderId(),
                result.recommendedFolderName()
        );
    }
}