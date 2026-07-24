package com.teaching.backend.domain.material.service.extract;

import java.time.LocalDateTime;

public record ParsedHtmlContent(
        String originalUrl,
        String title,
        String content,
        String thumbnailUrl,
        String author,
        LocalDateTime publishedAt
) {
}
