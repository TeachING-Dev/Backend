package com.teaching.backend.domain.teachingmap.enums;

import lombok.Getter;

@Getter
public enum AiGuideContentType {

    SUMMARY("요약"),
    CAUTION("주의");

    private final String description;

    AiGuideContentType(String description) {
        this.description = description;
    }
}
