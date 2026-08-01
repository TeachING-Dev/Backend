package com.teaching.backend.domain.material.dto.response;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.dto.ai.MaterialAiAnalysisPipelineResult;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.enums.MaterialAnalyzeResultType;
import com.teaching.backend.domain.material.enums.PlatformType;

import java.util.List;

public record MaterialAnalyzeResponse(
        Long materialAnalysisId,
        MaterialAnalyzeResultType resultType,
        Long materialId,
        Long existingMaterialId,
        Long existingFolderId,
        String summary,
        String originalUrl,
        String title,
        String platformType,
        String status,
        Integer chunkCount,
        Long recommendedFolderId,
        String recommendedFolderName,
        List<MaterialTagResponse> tags
) {

    public static MaterialAnalyzeResponse alreadyAnalyzed(
            Material material,
            MaterialAnalysis materialAnalysis,
            Long materialAnalysisId,
            Integer chunkCount,
            List<MaterialTagResponse> tags
    ) {
        PlatformType platformType = material.getPlatformType();

        return new MaterialAnalyzeResponse(
                materialAnalysisId,
                MaterialAnalyzeResultType.ALREADY_ANALYZED,
                null,
                material.getId(),
                material.getFolderId(),

                materialAnalysis  == null ? null : materialAnalysis.getSummary(),
                material.getOriginalUrl(),
                material.getTitle(),
                platformType == null ? null : platformType.name(),
                material.getAiStatus() == null ? null : material.getAiStatus().name(),
                chunkCount,
                material.getFolderId(),
                material.getFolder() == null ? null : material.getFolder().getName(),
                tags
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
                null,
                null,
                originalUrl,
                null,
                platformType == null ? null : platformType.name(),
                null,
                null,
                null,
                null,
                null
        );
    }

    public static MaterialAnalyzeResponse completed(
            MaterialAiAnalysisPipelineResult result,
            List<MaterialTagResponse> tags
    ) {
        PlatformType platformType = result.platformType();
        String title = result.extractedContent() == null ? null : result.extractedContent().title();

        return new MaterialAnalyzeResponse(
                result.materialAnalysisId(),
                MaterialAnalyzeResultType.ANALYSIS_COMPLETED,
                result.materialId(),
                null,
                null,
                result.summary(),
                result.originalUrl(),
                title,
                platformType == null ? null : platformType.name(),
                "COMPLETED",
                result.chunkCount(),
                result.recommendedFolderId(),
                result.recommendedFolderName(),
                tags
        );
    }
}
