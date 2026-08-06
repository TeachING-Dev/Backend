package com.teaching.backend.domain.material.dto.ai;

import java.util.List;

public record MaterialUrlAnalysisParseResult(
        MaterialAiAnalysisResult analysisResult,
        String recommendedFolderName
) {
}
