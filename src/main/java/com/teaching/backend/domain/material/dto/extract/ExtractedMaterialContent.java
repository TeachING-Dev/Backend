package com.teaching.backend.domain.material.dto.extract;

import com.teaching.backend.domain.material.enums.PlatformType;

import java.time.LocalDateTime;
import java.util.List;

public record ExtractedMaterialContent(
        String originalUrl,
        PlatformType platformType,
        String title,
        String content,
        String thumbnailUrl,
        String author,
        LocalDateTime publishedAt,
        List<MaterialImageCandidate> imageCandidates
) {

    public ExtractedMaterialContent(
            String originalUrl,
            PlatformType platformType,
            String title,
            String content,
            String thumbnailUrl,
            String author,
            LocalDateTime publishedAt
    ) {
        this(originalUrl, platformType, title, content, thumbnailUrl, author, publishedAt, List.of());
    }

    public ExtractedMaterialContent {
        imageCandidates = imageCandidates == null ? List.of() : List.copyOf(imageCandidates);
    }
}
