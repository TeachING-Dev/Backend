package com.teaching.backend.domain.material.dto.ai;

import java.util.List;

public record MaterialUrlAnalysisParseResult(
        MaterialAiAnalysisResult analysisResult,
        List<MaterialAiHighlightResult> highlights,
        String recommendedFolderName
) {
    public MaterialUrlAnalysisParseResult {
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
    }
}
