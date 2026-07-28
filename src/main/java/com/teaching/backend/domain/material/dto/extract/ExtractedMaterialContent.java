package com.teaching.backend.domain.material.dto.extract;

import com.teaching.backend.domain.material.enums.PlatformType;

import java.time.LocalDateTime;

public record ExtractedMaterialContent(
        String originalUrl,
        PlatformType platformType,
        String title,
        String content,
        String thumbnailUrl,
        String author,
        LocalDateTime publishedAt
) {
}
