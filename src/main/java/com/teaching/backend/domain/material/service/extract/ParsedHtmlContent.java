package com.teaching.backend.domain.material.service.extract;

import com.teaching.backend.domain.material.dto.extract.MaterialImageCandidate;

import java.time.LocalDateTime;
import java.util.List;

public record ParsedHtmlContent(
        String originalUrl,
        String title,
        String content,
        String thumbnailUrl,
        String author,
        LocalDateTime publishedAt,
        List<MaterialImageCandidate> imageCandidates
) {

    public ParsedHtmlContent(
            String originalUrl,
            String title,
            String content,
            String thumbnailUrl,
            String author,
            LocalDateTime publishedAt
    ) {
        this(originalUrl, title, content, thumbnailUrl, author, publishedAt, List.of());
    }

    public ParsedHtmlContent {
        imageCandidates = imageCandidates == null ? List.of() : List.copyOf(imageCandidates);
    }
}
