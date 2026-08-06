package com.teaching.backend.domain.material.dto.ai;

import com.teaching.backend.domain.material.enums.MaterialAiStageType;

public record MaterialAiStageResult(
        MaterialAiStageType stageType,
        MaterialAiAnalysisResult analysisResult,
        String recommendedFolderName
) {
    public MaterialAiStageResult(
            MaterialAiStageType stageType,
            MaterialAiAnalysisResult analysisResult
    ) {
        this(stageType, analysisResult, null);
    }
}
