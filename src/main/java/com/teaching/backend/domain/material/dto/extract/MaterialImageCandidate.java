package com.teaching.backend.domain.material.dto.extract;

public record MaterialImageCandidate(
        String url,
        String alt,
        String caption,
        String title,
        String sectionHeading,
        String context
) {
}
