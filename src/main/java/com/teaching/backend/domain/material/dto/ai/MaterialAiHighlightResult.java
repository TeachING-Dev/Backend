package com.teaching.backend.domain.material.dto.ai;

import com.teaching.backend.domain.material.enums.MaterialAiHighlightType;

public record MaterialAiHighlightResult(
        String text,
        MaterialAiHighlightType type
) {
}
