package com.teaching.backend.domain.enums;

import lombok.Getter;

@Getter
public enum HighlightType {

    CAUTION("주의"),
    MAIN("핵심");
    private final String description;

    HighlightType(String description) {
        this.description = description;
    }
}