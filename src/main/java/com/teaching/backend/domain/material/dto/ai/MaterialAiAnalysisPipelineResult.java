package com.teaching.backend.domain.material.dto.ai;

import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.enums.PlatformType;

import java.util.List;

public record MaterialAiAnalysisPipelineResult(
        Long materialId,
        Long userId,
        Long folderId,
        String summary,
        String fullAnalysis,
        String originalUrl,
        PlatformType platformType,
        ExtractedMaterialContent extractedContent,
        Long materialAnalysisId,
        int chunkCount,
        Long recommendedFolderId,
        String recommendedFolderName,
        List<String> tags
) {
    public MaterialAiAnalysisPipelineResult {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public MaterialAiAnalysisPipelineResult(
            Long materialId,
            Long userId,
            Long folderId,
            String summary,
            String fullAnalysis,
            String originalUrl,
            PlatformType platformType,
            ExtractedMaterialContent extractedContent,
            Long materialAnalysisId
    ) {
        this(
                materialId,
                userId,
                folderId,
                summary,
                fullAnalysis,
                originalUrl,
                platformType,
                extractedContent,
                materialAnalysisId,
                0,
                null,
                null,
                List.of()
        );
    }
}
