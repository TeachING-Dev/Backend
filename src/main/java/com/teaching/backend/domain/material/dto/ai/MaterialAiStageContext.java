package com.teaching.backend.domain.material.dto.ai;

import com.teaching.backend.domain.material.dto.extract.ExtractedMaterialContent;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.PlatformType;

import java.util.Optional;

public record MaterialAiStageContext(
        Long userId,
        Long folderId,
        String originalUrl,
        PlatformType platformType,
        ExtractedMaterialContent extractedContent,
        Material material,
        Optional<MaterialAiAnalysisResult> previousAnalysisResult
) {
}
