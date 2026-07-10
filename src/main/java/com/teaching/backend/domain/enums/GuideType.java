package com.teaching.backend.domain.enums;

import lombok.Getter;

@Getter
public enum GuideType {

    FRIENDLY("친절한 선생님", "friendly-teacher.svg"),
    ENCOURAGING("응원하는 선생님", "encouraging-teacher.svg"),
    STRICT("엄격한 선생님", "strict-teacher.svg");

    private final String description;
    private final String imagePath;

    GuideType(String description, String imagePath) {
        this.description = description;
        this.imagePath = imagePath;
    }
}