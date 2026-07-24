package com.teaching.backend.domain.material.enums;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MaterialAiHighlightType {

    CORE("핵심"),
    CAUTION("주의");

    private final String label;

    public static MaterialAiHighlightType fromLabel(String label) {
        if (label == null) {
            throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED);
        }

        for (MaterialAiHighlightType type : values()) {
            if (type.label.equals(label.trim())) {
                return type;
            }
        }
        throw new MaterialException(MaterialErrorCode.AI_ANALYSIS_PARSE_FAILED);
    }
}
