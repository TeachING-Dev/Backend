package com.teaching.backend.domain.teachingmap.enums;

import lombok.Getter;

@Getter
public enum AiGuideContentType {

    CAUTION("주의"),
    MAIN("핵심");

    private final String description;

    AiGuideContentType(String description) {
        this.description = description;
    }
}
