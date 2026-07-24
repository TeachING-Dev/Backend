package com.teaching.backend.domain.material.service.extract;

public record HtmlDocument(
        String originalUrl,
        String body,
        String contentType
) {
}
