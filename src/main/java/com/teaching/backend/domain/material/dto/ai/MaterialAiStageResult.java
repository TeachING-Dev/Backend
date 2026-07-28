package com.teaching.backend.domain.material.dto.ai;

import com.teaching.backend.domain.material.enums.MaterialAiStageType;

import java.util.List;

public record MaterialAiStageResult(
        MaterialAiStageType stageType,
        MaterialAiAnalysisResult analysisResult,
        List<MaterialAiHighlightResult> highlights,
        String recommendedFolderName
) {
    public MaterialAiStageResult {
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
    }

    public MaterialAiStageResult(
            MaterialAiStageType stageType,
            MaterialAiAnalysisResult analysisResult
    ) {
        this(stageType, analysisResult, List.of(), null);
    }
}
