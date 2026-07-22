package com.teaching.backend.domain.material.dto.request;

import com.teaching.backend.domain.material.enums.PlatformType;

public record MaterialAnalysisGenerateRequest(
        String title,
        String originalUrl,
        String content,
        PlatformType platformType
) {
}
