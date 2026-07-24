package com.teaching.backend.domain.material.dto.extract;

import com.teaching.backend.domain.material.enums.PlatformType;

public record MaterialAnalysisPreparationResult(
        Long userId,
        Long folderId,
        String originalUrl,
        PlatformType platformType,
        ExtractedMaterialContent extractedContent
) {
}
