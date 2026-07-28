package com.teaching.backend.domain.material.dto.ai;

import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.enums.PlatformType;

import java.util.List;

public record MaterialAiAnalysisPipelineResult(
        Long materialId,
        Long userId,
        String originalUrl,
        PlatformType platformType,
        ExtractedMaterialContent extractedContent,
        Long materialAnalysisId,
        int chunkCount,
        List<MaterialAiHighlightResult> highlights,
        Long recommendedFolderId,
        String recommendedFolderName,
        List<String> tags
) {
    public MaterialAiAnalysisPipelineResult {
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public MaterialAiAnalysisPipelineResult(
            Long materialId,
            Long userId,
            String originalUrl,
            PlatformType platformType,
            ExtractedMaterialContent extractedContent,
            Long materialAnalysisId
    ) {
        this(
                materialId,
                userId,
                originalUrl,
                platformType,
                extractedContent,
                materialAnalysisId,
                0,
                List.of(),
                null,
                null,
                List.of()
        );
    }
}
